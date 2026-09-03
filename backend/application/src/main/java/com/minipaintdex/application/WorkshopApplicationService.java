package com.minipaintdex.application;
import com.minipaintdex.application.result.ImportPaintPotsResult;
import com.minipaintdex.application.view.PaintPotView;
import com.minipaintdex.application.query.SearchPaintPotsQuery;
import com.minipaintdex.application.command.ImportPaintPotsCommand;
import com.minipaintdex.application.command.RegisterPaintPotCommand;
import com.minipaintdex.application.command.ObservePaintPotCommand;
import com.minipaintdex.application.command.OpenPaintPotCommand;
import com.minipaintdex.application.command.ChangePaintPotPossessionCommand;
import com.minipaintdex.application.command.AddPaintPotNoteCommand;
import com.minipaintdex.application.command.AddPaintPotPhotoCommand;

import com.minipaintdex.application.command.AddWorkshopPaintableCommand;
import com.minipaintdex.application.command.AddWorkshopPaintableCommentCommand;
import com.minipaintdex.application.command.AddWorkshopPaintablePhotoCommand;
import com.minipaintdex.application.command.AssignWorkshopRecipeCommand;
import com.minipaintdex.application.command.CreatePaintingProjectCommand;
import com.minipaintdex.application.command.CreateWorkshopRecipeCommand;
import com.minipaintdex.application.command.SetShoppingListEntryCheckedCommand;
import com.minipaintdex.application.command.TransitionPaintingProjectCommand;
import com.minipaintdex.application.command.TransitionWorkshopPaintableStageCommand;
import com.minipaintdex.application.command.TransitionWorkshopRecipeCommand;
import com.minipaintdex.application.event.PublicationReceipt;
import com.minipaintdex.application.result.CreatePaintingProjectResult;
import com.minipaintdex.application.result.PageResult;
import com.minipaintdex.application.query.PageQuery;
import com.minipaintdex.application.query.SearchPaintProductsQuery;
import com.minipaintdex.application.port.SnapshotRepository;
import com.minipaintdex.application.usecase.MarketCatalogUseCases;
import com.minipaintdex.application.usecase.WorkshopUseCases;
import com.minipaintdex.application.view.PaintingProjectView;
import com.minipaintdex.application.view.PaintingProjectImportPreviewView;
import com.minipaintdex.application.view.GuideReconciliationView;
import com.minipaintdex.application.view.ShoppingListEntryView;
import com.minipaintdex.application.view.WorkshopPaintableView;
import com.minipaintdex.application.view.WorkshopOverviewView;
import com.minipaintdex.application.view.WorkshopRecipeView;
import com.minipaintdex.application.view.WorkshopPaintStockView;
import com.minipaintdex.application.view.PaintFacetsView;
import com.minipaintdex.domain.event.EventEnvelope;

import java.util.List;
import java.util.Objects;

/** Cohesive command/query service for workshop aggregates and their ledger projections. */
public final class WorkshopApplicationService implements WorkshopUseCases {
    private final WorkshopCommandService commands;
    private final WorkshopQueryService queries;
    private final WorkshopPaintQueryService paintQueries;
    private final PaintPotQueryService potQueries;
    private final PaintPotPhotoService potPhotos;
    private final com.minipaintdex.application.query.PaintSearchPolicy searchPolicy;

    public WorkshopApplicationService(
            WorkshopCommandService commands,
            WorkshopQueryService queries,
            MarketCatalogUseCases market,
            SnapshotRepository snapshots, com.minipaintdex.application.query.PaintSearchPolicy searchPolicy, PaintPotPhotoService potPhotos) {
        this.potPhotos = Objects.requireNonNull(potPhotos);
        this.searchPolicy = Objects.requireNonNull(searchPolicy);
        this.commands = Objects.requireNonNull(commands);
        this.queries = Objects.requireNonNull(queries);
        this.potQueries = new PaintPotQueryService(snapshots, market);
        this.paintQueries = new WorkshopPaintQueryService(
                Objects.requireNonNull(market), Objects.requireNonNull(snapshots));
    }

    @Override public com.minipaintdex.application.result.PaintSearchResult<com.minipaintdex.application.view.WorkshopPaintStockView> searchWorkshopPaintStocks(
            com.minipaintdex.application.query.PaintSearchQuery query) {
        return paintQueries.search(query, searchPolicy);
    }

    @Override public WorkshopOverviewView workshopOverview() { return queries.workshopOverview(); }
    @Override public ImportPaintPotsResult importPaintPots(ImportPaintPotsCommand command) { return commands.importPaintPots(command); }
    @Override public ImportPaintPotsResult registerPaintPot(RegisterPaintPotCommand command) { return commands.registerPaintPot(command); }
    @Override public PublicationReceipt observePaintPot(ObservePaintPotCommand command) { return commands.observePaintPot(command); }
    @Override public PublicationReceipt openPaintPot(OpenPaintPotCommand command) { return commands.openPaintPot(command); }
    @Override public PublicationReceipt changePaintPotPossession(ChangePaintPotPossessionCommand command) { return commands.changePaintPotPossession(command); }
    @Override public PublicationReceipt addPaintPotNote(AddPaintPotNoteCommand command) { return commands.addPaintPotNote(command); }
    @Override public PublicationReceipt addPaintPotPhoto(AddPaintPotPhotoCommand command) { return commands.addPaintPotPhoto(command); }
    @Override public com.minipaintdex.application.result.PaintPotPhotoPreview previewPaintPotPhoto(
            com.minipaintdex.application.query.PreviewPaintPotPhotoQuery query) {
        potQueries.get(query.paintPotId());
        return potPhotos.preview(query.contentType(), query.content(), query.correlationId());
    }
    @Override public PageResult<PaintPotView> searchPaintPots(SearchPaintPotsQuery query) { return potQueries.search(query); }
    @Override public PaintPotView getPaintPot(String id) { return potQueries.get(id); }
    @Override public PaintFacetsView workshopPaintStockFacets(
            SearchPaintProductsQuery filters, boolean manufacturerSheetOnly, boolean realResultOnly) {
        return paintQueries.facets(filters, manufacturerSheetOnly, realResultOnly);
    }
    @Override public List<PaintingProjectView> listPaintingProjects() { return queries.listPaintingProjects(); }
    @Override public List<WorkshopPaintableView> listWorkshopPaintables(String paintingProjectId) {
        return queries.listWorkshopPaintables(paintingProjectId);
    }
    @Override public WorkshopPaintableView.Detail getWorkshopPaintable(String workshopPaintableId) { return queries.getWorkshopPaintable(workshopPaintableId); }
    @Override public PaintingProjectImportPreviewView previewPaintingProjectImport(String paintableProductId) {
        return queries.previewPaintingProjectImport(paintableProductId);
    }
    @Override public GuideReconciliationView reconcileMarketPaintingGuide(String guideId) {
        return queries.reconcileMarketPaintingGuide(guideId);
    }
    @Override public CreatePaintingProjectResult createPaintingProject(CreatePaintingProjectCommand command) {
        return commands.createPaintingProject(command);
    }
    @Override public PublicationReceipt transitionPaintingProject(TransitionPaintingProjectCommand command) {
        return commands.transitionPaintingProject(command);
    }
    @Override public PublicationReceipt addWorkshopPaintable(AddWorkshopPaintableCommand command) {
        return commands.addWorkshopPaintable(command);
    }
    @Override public PublicationReceipt transitionWorkshopPaintableStage(TransitionWorkshopPaintableStageCommand command) {
        return commands.transitionWorkshopPaintableStage(command);
    }
    @Override public PublicationReceipt addWorkshopPaintableComment(AddWorkshopPaintableCommentCommand command) {
        return commands.addWorkshopPaintableComment(command);
    }
    @Override public PublicationReceipt addWorkshopPaintablePhoto(AddWorkshopPaintablePhotoCommand command) {
        return commands.addWorkshopPaintablePhoto(command);
    }
    @Override public List<WorkshopRecipeView> listWorkshopRecipes(String paintableComponentId) {
        return queries.listWorkshopRecipes(paintableComponentId);
    }
    @Override public PublicationReceipt createWorkshopRecipe(CreateWorkshopRecipeCommand command) {
        return commands.createWorkshopRecipe(command);
    }
    @Override public PublicationReceipt transitionWorkshopRecipe(TransitionWorkshopRecipeCommand command) {
        return commands.transitionWorkshopRecipe(command);
    }
    @Override public PublicationReceipt assignWorkshopRecipe(AssignWorkshopRecipeCommand command) {
        return commands.assignWorkshopRecipe(command);
    }
    @Override public List<ShoppingListEntryView> listShoppingListEntries() { return queries.listShoppingListEntries(); }
    @Override public PublicationReceipt setShoppingListEntryChecked(SetShoppingListEntryCheckedCommand command) {
        return commands.setShoppingListEntryChecked(command);
    }
    @Override public List<EventEnvelope> listActivity(String paintingProjectId) { return queries.listActivity(paintingProjectId); }
}
