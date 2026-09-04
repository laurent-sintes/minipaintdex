package com.minipaintdex.application.usecase;

import com.minipaintdex.application.storage.StorageContracts.*;
import com.minipaintdex.application.result.PageResult;
import com.minipaintdex.application.event.PublicationReceipt;

/**
 * Storage capabilities shared by REST and CLI. Reads return detached committed projections;
 * lists are pageable and ordered by stable identity (pots by name then identity). Unknown IDs
 * raise not_found; invalid geometry/selections raise invalid_input. No resources are retained.
 * Preview is read-only and bounded by the configured pot limit. Confirmation rejects a stale
 * snapshot token and preserves locks. Mutations require an idempotency key, serialize with other
 * workshop commands, include pending accepted events, and return durable acceptance, not a
 * promise that projections have caught up. Reusing a key cannot apply a second mutation.
 */
public interface WorkshopStorageUseCases {
    PageResult<RackView> listWorkshopRacks(ListRacks query);
    RackDetail getWorkshopRack(GetRack query);
    PageResult<PotView> searchStoragePots(SearchPots query);
    Proposal previewPaintStorage(Preview query);
    PublicationReceipt addWorkshopRacks(AddRacks command);
    PublicationReceipt saveWorkshopRack(SaveRack command);
    PublicationReceipt identifyPaintPotContainer(IdentifyContainer command);
    PublicationReceipt confirmPaintStorage(Confirm command);
    PublicationReceipt setPaintPotPlacement(SetPlacement command);
}
