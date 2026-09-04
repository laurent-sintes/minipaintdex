package com.minipaintdex.application.port;

import com.minipaintdex.domain.market.storage.RackCatalog;

/**
 * Atomically replaces the validated rack reference generation under the shared storage lock.
 * The expected revision must match the current revision; replacement advances it once.
 * Replaying an identical replacement is idempotent. Stale or malformed writes fail without
 * changing storage. Reads after completion see the new immutable generation. Callers own no
 * resource lifetime and cannot remove identities through this port's application use case.
 */
@FunctionalInterface
public interface RackCatalogWriter {
    RackCatalog replace(RackCatalog catalog, long expectedRevision);
}
