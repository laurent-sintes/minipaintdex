package com.minipaintdex.bootstrap;

import com.minipaintdex.application.document.StructuredDocument;
import com.minipaintdex.application.port.DataSnapshot;
import com.minipaintdex.domain.shared.DomainException;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class CachedMarketCatalogReaderTest {
    @Test void reusesTypedMarketForUnchangedSourcesAndDoesNotPublishAnInvalidReplacement() {
        var empty = new StructuredDocument(List.of());
        var source = new AtomicReference<>(new DataSnapshot(empty, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), java.util.List.of()));
        var reader = new CachedMarketCatalogReader(source::get);
        var first = reader.load();
        source.set(new DataSnapshot(empty, List.of(), List.of(), List.of(), List.of(empty), List.of(), List.of(), java.util.List.of()));
        assertSame(first, reader.load(), "Changes outside Market must not invalidate its typed projection");
        source.set(new DataSnapshot(empty, List.of(empty), List.of(), List.of(), List.of(), List.of(), List.of(), java.util.List.of()));
        assertThrows(DomainException.class, reader::load);
        source.set(new DataSnapshot(empty, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), java.util.List.of()));
        assertSame(first, reader.load(), "A failed translation must not poison the published cache");
    }
}
