package com.minipaintdex.application;

import com.minipaintdex.application.command.AddWorkshopItemCommand;
import com.minipaintdex.application.command.AddWorkshopItemCommentCommand;
import com.minipaintdex.application.command.AddWorkshopItemPhotoCommand;
import com.minipaintdex.application.command.AssignWorkshopRecipeCommand;
import com.minipaintdex.application.command.CreatePaintingProjectCommand;
import com.minipaintdex.application.command.CreateWorkshopRecipeCommand;
import com.minipaintdex.application.command.SetShoppingItemStatusCommand;
import com.minipaintdex.application.command.TransitionPaintingProjectCommand;
import com.minipaintdex.application.command.TransitionStageCommand;
import com.minipaintdex.application.command.TransitionWorkshopRecipeCommand;
import com.minipaintdex.application.event.PublicationReceipt;
import com.minipaintdex.application.result.CreatePaintingProjectResult;
import com.minipaintdex.application.result.PageResult;
import com.minipaintdex.application.query.PageQuery;
import com.minipaintdex.application.query.SearchMarketPaintsQuery;
import com.minipaintdex.application.port.SnapshotRepository;
import com.minipaintdex.application.usecase.MarketCatalogUseCases;
import com.minipaintdex.application.usecase.WorkshopUseCases;
import com.minipaintdex.application.view.PaintingProjectView;
import com.minipaintdex.application.view.ProductImportPreviewView;
import com.minipaintdex.application.view.GuideReconciliationView;
import com.minipaintdex.application.view.ShoppingItemView;
import com.minipaintdex.application.view.WorkshopItemView;
import com.minipaintdex.application.view.WorkshopOverviewView;
import com.minipaintdex.application.view.WorkshopRecipeView;
import com.minipaintdex.application.view.WorkshopPaintView;
import com.minipaintdex.application.view.PaintFacetsView;
import com.minipaintdex.domain.event.EventEnvelope;

import java.util.List;
import java.util.Objects;

/** Cohesive command/query service for workshop aggregates and their ledger projections. */
public final class WorkshopApplicationService implements WorkshopUseCases {
    private final MiniPaintDexService kernel;
    private final WorkshopPaintQueryService paintQueries;

    public WorkshopApplicationService(
            MiniPaintDexService kernel,
            MarketCatalogUseCases market,
            SnapshotRepository snapshots) {
        this.kernel = Objects.requireNonNull(kernel);
        this.paintQueries = new WorkshopPaintQueryService(
                Objects.requireNonNull(market), Objects.requireNonNull(snapshots));
    }

    @Override public WorkshopOverviewView workshopOverview() { return kernel.workshopOverview(); }
    @Override public PageResult<WorkshopPaintView> searchWorkshopPaintPage(
            SearchMarketPaintsQuery filters, boolean manufacturerSheetOnly,
            boolean realResultOnly, PageQuery page) {
        return paintQueries.page(filters, manufacturerSheetOnly, realResultOnly, page);
    }
    @Override public PaintFacetsView workshopPaintFacets() { return paintQueries.facets(); }
    @Override public List<PaintingProjectView> listPaintingProjects() { return kernel.listPaintingProjects(); }
    @Override public List<WorkshopItemView> listWorkshopItems(String projectId) {
        return kernel.listWorkshopItems(projectId);
    }
    @Override public WorkshopItemView.Detail getWorkshopItem(String itemId) { return kernel.getWorkshopItem(itemId); }
    @Override public ProductImportPreviewView previewProductImport(String productId) {
        return kernel.previewProductImport(productId);
    }
    @Override public GuideReconciliationView reconcileMarketPaintingGuide(String guideId) {
        return kernel.reconcileMarketPaintingGuide(guideId);
    }
    @Override public CreatePaintingProjectResult createPaintingProject(CreatePaintingProjectCommand command) {
        return kernel.createPaintingProject(command);
    }
    @Override public PublicationReceipt transitionPaintingProject(TransitionPaintingProjectCommand command) {
        return kernel.transitionPaintingProject(command);
    }
    @Override public PublicationReceipt addWorkshopItem(AddWorkshopItemCommand command) {
        return kernel.addWorkshopItem(command);
    }
    @Override public PublicationReceipt transitionStage(TransitionStageCommand command) {
        return kernel.transitionStage(command);
    }
    @Override public PublicationReceipt addWorkshopItemComment(AddWorkshopItemCommentCommand command) {
        return kernel.addWorkshopItemComment(command);
    }
    @Override public PublicationReceipt addWorkshopItemPhoto(AddWorkshopItemPhotoCommand command) {
        return kernel.addWorkshopItemPhoto(command);
    }
    @Override public List<WorkshopRecipeView> listWorkshopRecipes(String catalogItemId) {
        return kernel.listWorkshopRecipes(catalogItemId);
    }
    @Override public PublicationReceipt createWorkshopRecipe(CreateWorkshopRecipeCommand command) {
        return kernel.createWorkshopRecipe(command);
    }
    @Override public PublicationReceipt transitionWorkshopRecipe(TransitionWorkshopRecipeCommand command) {
        return kernel.transitionWorkshopRecipe(command);
    }
    @Override public PublicationReceipt assignWorkshopRecipe(AssignWorkshopRecipeCommand command) {
        return kernel.assignWorkshopRecipe(command);
    }
    @Override public List<ShoppingItemView> listShoppingItems() { return kernel.listShoppingItems(); }
    @Override public PublicationReceipt setShoppingItemStatus(SetShoppingItemStatusCommand command) {
        return kernel.setShoppingItemStatus(command);
    }
    @Override public List<EventEnvelope> listActivity(String projectId) { return kernel.listActivity(projectId); }
}
