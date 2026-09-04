package com.minipaintdex.application;

import com.minipaintdex.application.storage.*;
import com.minipaintdex.application.storage.StorageContracts.*;
import com.minipaintdex.application.port.SnapshotRepository;
import com.minipaintdex.application.query.PageQuery;
import com.minipaintdex.application.result.PageResult;
import com.minipaintdex.domain.workshop.storage.*;
import com.minipaintdex.domain.shared.DomainException;
import java.util.*;
import java.util.stream.Collectors;

final class WorkshopStorageQueries {
    private final SnapshotRepository snapshots;
    private final PaintStoragePolicy policy;
    WorkshopStorageQueries(SnapshotRepository snapshots, PaintStoragePolicy policy) { this.snapshots = snapshots; this.policy = policy; }
    PageResult<RackView> list(ListRacks query) {
        var context = StorageProjection.context(snapshots.load());
        return page(context.rackAggregates().stream().map(context::rackView).toList(), query.page());
    }
    RackDetail get(GetRack query) {
        var context = StorageProjection.context(snapshots.load());
        var rack = context.rackAggregates().stream().filter(value -> value.id().equals(query.workshopRackId())).findFirst()
                .orElseThrow(() -> new DomainException("not_found", "Workshop rack not found."));
        return new RackDetail(context.rackView(rack), context.potViews().stream().filter(value -> value.placement() != null
                && value.placement().workshopRackId().equals(rack.id())).toList(), context.token(), query.correlationId());
    }
    PageResult<PotView> search(SearchPots query) {
        var context = StorageProjection.context(snapshots.load());
        var owned = context.pots().stream().filter(StorageCompatibility.Pot::owned).map(StorageCompatibility.Pot::paintPotId).collect(Collectors.toSet());
        String text = Objects.toString(query.query(), "").toLowerCase(Locale.ROOT);
        return page(context.potViews().stream().filter(value -> owned.contains(value.paintPotId()))
                .filter(value -> !query.unplacedOnly() || value.placement() == null)
                .filter(value -> (value.name() + " " + value.brand() + " " + value.range() + " " + value.paintPotId()).toLowerCase(Locale.ROOT).contains(text))
                .sorted(Comparator.comparing(PotView::name).thenComparing(PotView::paintPotId)).toList(), query.page());
    }
    Proposal preview(Preview query) {
        var context = StorageProjection.context(snapshots.load());
        var ids = query.allOwnedPots() ? context.pots().stream().filter(StorageCompatibility.Pot::owned)
                .map(StorageCompatibility.Pot::paintPotId).collect(Collectors.toSet()) : query.paintPotIds();
        var result = new PaintStorageOrganizer(policy).propose(context.pots(), context.racks(), context.storage().placements(),
                ids, query.workshopRackIds().isEmpty() ? context.racks().stream().filter(StorageCompatibility.Rack::owned)
                        .map(StorageCompatibility.Rack::workshopRackId).collect(Collectors.toSet()) : query.workshopRackIds(),
                query.mode(), query.allowEstimates(), query.preserveExisting());
        return new Proposal(context.token(), result, context.potViews().stream().filter(pot -> ids.contains(pot.paintPotId())).toList(), query.correlationId());
    }
    static <T> PageResult<T> page(List<T> values, PageQuery query) {
        if (query == null || !query.sort().isEmpty()) throw new DomainException("invalid_input", "Use the stable default ordering.");
        var start = Math.min(query.offset(), values.size());
        return new PageResult<>(values.subList(start, Math.min(start + query.size(), values.size())), query.page(), query.size(), values.size());
    }
}
