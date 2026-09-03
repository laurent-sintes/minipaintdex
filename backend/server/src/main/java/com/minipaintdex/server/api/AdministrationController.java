package com.minipaintdex.server.api;

import com.minipaintdex.application.command.ApplyPaintProductChangeSetCommand;
import com.minipaintdex.application.command.ApplyMarketPaintableProductChangeSetCommand;
import com.minipaintdex.application.document.StructuredDocument;
import com.minipaintdex.application.result.ApplyPaintProductChangeSetResult;
import com.minipaintdex.application.result.ApplyMarketPaintableProductChangeSetResult;
import com.minipaintdex.application.usecase.AdministrationUseCases;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
final class AdministrationController {
    private final AdministrationUseCases administration;

    AdministrationController(AdministrationUseCases administration) {
        this.administration = administration;
    }

    @PostMapping("/market/paint-changesets")
    ResultResponse<ApplyPaintProductChangeSetResult> applyPaintChangeSet(
            @Valid @RequestBody ApplyPaintChangeSetRequest request,
            @RequestParam(defaultValue = "true") boolean dryRun) {
        var operations = request.operations().stream()
                .map(operation -> new ApplyPaintProductChangeSetCommand.Operation(
                        operation.action(), operation.previousId(), document(operation.record()),
                        operation.workshopQuantityDelta() == null ? 0 : operation.workshopQuantityDelta(),
                        Boolean.TRUE.equals(operation.confirmedRemoval())))
                .toList();
        return new ResultResponse<>(administration.applyPaintProductChangeSet(new ApplyPaintProductChangeSetCommand(
                request.schemaVersion(), request.kind(), operations, dryRun,
                request.catalogEditions().stream().map(AdministrationController::document).toList(),
                request.paintUsageGuides().stream().map(AdministrationController::document).toList())));
    }

    @PostMapping("/market/paintable-product-changesets")
    ResultResponse<ApplyMarketPaintableProductChangeSetResult> applyPaintableProductChangeSet(
            @Valid @RequestBody ApplyPaintableProductChangeSetRequest request,
            @RequestParam(defaultValue = "true") boolean dryRun) {
        return new ResultResponse<>(administration.applyMarketPaintableProductChangeSet(
                new ApplyMarketPaintableProductChangeSetCommand(
                        request.schemaVersion(), request.kind(), document(request.product()),
                        request.paintingGuides().stream().map(AdministrationController::document).toList(),
                        dryRun, request.actorId(), request.correlationId())));
    }

    @PostMapping("/projections/rebuild")
    ProjectionResponse rebuildProjections() {
        return new ProjectionResponse(administration.rebuildProjections());
    }

    private static StructuredDocument document(Map<String, Object> values) {
        return new StructuredDocument(values.entrySet().stream()
                .map(entry -> new StructuredDocument.Field(entry.getKey(), documentValue(entry.getValue())))
                .toList());
    }

    private static StructuredDocument.Value documentValue(Object value) {
        if (value == null) return new StructuredDocument.NullValue();
        if (value instanceof Map<?, ?> values) {
            var normalized = new java.util.LinkedHashMap<String, Object>();
            values.forEach((key, entry) -> normalized.put(String.valueOf(key), entry));
            return new StructuredDocument.ObjectValue(document(normalized));
        }
        if (value instanceof java.util.List<?> values) {
            return new StructuredDocument.ArrayValue(values.stream()
                    .map(AdministrationController::documentValue).toList());
        }
        if (value instanceof Number number) return new StructuredDocument.NumberValue(number);
        if (value instanceof Boolean bool) return new StructuredDocument.BooleanValue(bool);
        return new StructuredDocument.Text(String.valueOf(value));
    }
}
