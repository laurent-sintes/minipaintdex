package com.minipaintdex.adapter.lucene;

import com.minipaintdex.application.port.PaintProductSearchIndex;
import com.minipaintdex.application.query.PaintSearchPolicy;
import com.minipaintdex.domain.market.paint.PaintProduct;
import com.minipaintdex.domain.shared.DomainException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** One rebuildable heap index owned by the application lifecycle, never a source of truth. */
public final class LucenePaintProductSearchIndex implements PaintProductSearchIndex, AutoCloseable {
    private final PaintSearchPolicy policy;
    private final Analyzer analyzer = new Analyzer() {
        @Override protected TokenStreamComponents createComponents(String field) {
            var tokenizer = new StandardTokenizer();
            return new TokenStreamComponents(tokenizer, new ASCIIFoldingFilter(new LowerCaseFilter(tokenizer)));
        }
    };
    private Generation generation;
    private boolean closed;

    public LucenePaintProductSearchIndex(PaintSearchPolicy policy) {
        this.policy = Objects.requireNonNull(policy);
    }

    // The local catalog is small. Serialize searches/rebuilds so an old reader cannot be closed
    // under a query. Build separately, then swap only a complete generation; failures keep the
    // previous reader intact but fail the current request instead of silently serving stale facts.
    @Override public synchronized List<String> rank(List<PaintProduct> products, String text) {
        if (closed) throw new DomainException("search_unavailable", "Paint search is closed");
        Objects.requireNonNull(products);
        var input = text == null ? "" : text.trim();
        if (input.length() > policy.maxQueryLength()) throw invalid("Search text is too long");
        try {
            var query = query(input);
            if (generation == null || !generation.products().equals(products)) {
                var replacement = build(products);
                var previous = generation;
                generation = replacement;
                if (previous != null) previous.close();
            }
            if (products.isEmpty()) return List.of();
            var searcher = new IndexSearcher(generation.reader());
            var hits = searcher.search(query, products.size()).scoreDocs;
            var stored = searcher.storedFields();
            var results = new ArrayList<Hit>(hits.length);
            for (var hit : hits) {
                var document = stored.document(hit.doc);
                results.add(new Hit(document.get("id"), document.get("displayName"), hit.score));
            }
            return results.stream().sorted(Comparator.comparingDouble(Hit::score).reversed()
                    .thenComparing(Hit::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Hit::id))
                    .map(Hit::id).toList();
        } catch (IOException exception) {
            throw new DomainException("search_unavailable", "Unable to build or query the paint search index");
        }
    }

    private Generation build(List<PaintProduct> products) throws IOException {
        var directory = new ByteBuffersDirectory();
        try {
            try (var writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {
                for (var paint : products) {
                    var document = new Document();
                    document.add(new StoredField("id", paint.id()));
                    document.add(new StoredField("displayName", paint.name()));
                    document.add(new StringField("exactName", normalize(paint.name()), Field.Store.NO));
                    document.add(new StringField("reference", reference(paint.reference()), Field.Store.NO));
                    document.add(new TextField("name", paint.name(), Field.Store.NO));
                    document.add(new TextField("referenceText", value(paint.reference()), Field.Store.NO));
                    document.add(new TextField("catalog", String.join(" ", paint.brand(), paint.manufacturer(),
                            paint.range(), String.join(" ", paint.brandAliases())), Field.Store.NO));
                    var profile = paint.profile();
                    document.add(new TextField("metadata", String.join(" ", value(paint.color().family()),
                            String.join(" ", paint.tags()), String.join(" ", profile.roleIds()),
                            String.join(" ", profile.applicationMethodIds()), String.join(" ", profile.effectIds()),
                            profile.applicationSystem().id(), profile.coverage().id(), profile.finish().id(),
                            profile.undercoat().tone().id(), profile.medium().id()), Field.Store.NO));
                    writer.addDocument(document);
                }
            }
            return new Generation(List.copyOf(products), directory, DirectoryReader.open(directory));
        } catch (IOException | RuntimeException exception) {
            try { directory.close(); } catch (Exception cleanup) { exception.addSuppressed(cleanup); }
            throw exception;
        }
    }

    private Query query(String input) throws IOException {
        if (input.isBlank()) return new MatchAllDocsQuery();
        var terms = new ArrayList<String>();
        try (var tokens = analyzer.tokenStream("name", input)) {
            var term = tokens.addAttribute(CharTermAttribute.class);
            tokens.reset();
            while (tokens.incrementToken()) {
                terms.add(term.toString());
                if (terms.size() > policy.maxTerms()) throw invalid("Too many search terms");
            }
            tokens.end();
        }
        if (terms.isEmpty()) return new MatchNoDocsQuery();
        var allTerms = new BooleanQuery.Builder();
        for (var term : terms) {
            var alternatives = new ArrayList<Query>();
            alternatives.add(word("name", term, policy.nameBoost()));
            alternatives.add(word("catalog", term, policy.catalogBoost()));
            alternatives.add(word("metadata", term, policy.metadataBoost()));
            alternatives.add(word("referenceText", term, policy.nameBoost()));
            if (policy.maxEdits() > 0 && term.length() >= policy.fuzzyMinLength()
                    && term.codePoints().allMatch(Character::isLetter)) {
                alternatives.add(boost(new FuzzyQuery(new Term("name", term), policy.maxEdits(),
                        1, policy.maxExpansions(), true), policy.fuzzyBoost()));
            }
            allTerms.add(new DisjunctionMaxQuery(alternatives, 0), BooleanClause.Occur.MUST);
        }
        var alternatives = new BooleanQuery.Builder()
                .add(allTerms.build(), BooleanClause.Occur.SHOULD)
                .add(boost(new TermQuery(new Term("exactName", normalize(input))), policy.exactNameBoost()), BooleanClause.Occur.SHOULD);
        var reference = reference(input);
        if (!reference.isBlank()) {
            alternatives.add(boost(new TermQuery(new Term("reference", reference)), policy.exactReferenceBoost()), BooleanClause.Occur.SHOULD);
            alternatives.add(boost(new PrefixQuery(new Term("reference", reference)), policy.prefixBoost()), BooleanClause.Occur.SHOULD);
        }
        return alternatives.build();
    }

    private Query word(String field, String term, float weight) {
        return new DisjunctionMaxQuery(List.of(
                boost(new TermQuery(new Term(field, term)), weight),
                boost(new PrefixQuery(new Term(field, term)), weight * policy.prefixBoost())), 0);
    }

    private static Query boost(Query query, float boost) { return new BoostQuery(query, boost); }
    private static String value(String value) { return value == null ? "" : value; }
    private static String normalize(String value) {
        return Normalizer.normalize(value(value), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
    private static String reference(String value) { return normalize(value).replaceAll("[^\\p{L}\\p{N}]", ""); }
    private static DomainException invalid(String message) { return new DomainException("invalid_input", message); }

    @Override public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;
        try { if (generation != null) generation.close(); }
        finally { generation = null; analyzer.close(); }
    }

    private record Hit(String id, String name, float score) {}
    private record Generation(List<PaintProduct> products, ByteBuffersDirectory directory, DirectoryReader reader)
            implements AutoCloseable {
        @Override public void close() throws IOException {
            try { reader.close(); } finally { directory.close(); }
        }
    }
}
