package com.minipaintdex.application.port;

import com.minipaintdex.domain.market.paint.PaintProduct;
import java.util.List;

/**
 * Searches only canonical Market paint facts in the supplied immutable catalog generation.
 * Returns distinct product IDs ordered by decreasing relevance, then name and stable ID;
 * blank text returns all IDs in name/ID order. All analyzed terms must match. Implementations
 * normalize case/accents, support word prefixes and bounded typo tolerance in names, never
 * fuzzy numeric references. Source evidence and Workshop state are excluded.
 *
 * <p>Concurrent calls see complete indexes of their supplied generation, including deletions.
 * Indexes are disposable caches: no domain writes, events or idempotency keys are involved.
 * Invalid/overlong text raises invalid_input; infrastructure failure raises search_unavailable
 * without returning partial results. Returned immutable lists retain no open resources.
 * The adapter owner initializes and closes its index after in-flight reads have finished.</p>
 */
@FunctionalInterface
public interface PaintProductSearchIndex {
    List<String> rank(List<PaintProduct> products, String text);
}
