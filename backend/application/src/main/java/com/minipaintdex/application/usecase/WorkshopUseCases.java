package com.minipaintdex.application.usecase;

import com.minipaintdex.application.command.AddWorkshopItemCommand;
import com.minipaintdex.application.command.AddWorkshopItemCommentCommand;
import com.minipaintdex.application.command.AddWorkshopItemPhotoCommand;
import com.minipaintdex.application.command.AssignWorkshopRecipeCommand;
import com.minipaintdex.application.command.CreatePaintingProjectCommand;
import com.minipaintdex.application.command.CreateWorkshopRecipeCommand;
import com.minipaintdex.application.command.SetShoppingItemStatusCommand;
import com.minipaintdex.application.command.TransitionStageCommand;
import com.minipaintdex.application.command.TransitionWorkshopRecipeCommand;
import com.minipaintdex.application.command.TransitionPaintingProjectCommand;
import com.minipaintdex.application.event.PublicationReceipt;
import com.minipaintdex.application.result.CreatePaintingProjectResult;
import com.minipaintdex.application.result.PageResult;
import com.minipaintdex.application.query.PageQuery;
import com.minipaintdex.application.query.SearchMarketPaintsQuery;
import com.minipaintdex.domain.event.EventEnvelope;
import com.minipaintdex.application.view.PaintingProjectView;
import com.minipaintdex.application.view.ProductImportPreviewView;
import com.minipaintdex.application.view.GuideReconciliationView;
import com.minipaintdex.application.view.ShoppingItemView;
import com.minipaintdex.application.view.WorkshopItemView;
import com.minipaintdex.application.view.WorkshopOverviewView;
import com.minipaintdex.application.view.WorkshopRecipeView;
import com.minipaintdex.application.view.WorkshopPaintView;
import com.minipaintdex.application.view.PaintFacetsView;

import java.util.List;

/** Commands and queries for the owner's workshop aggregates. */
public interface WorkshopUseCases {
    /** Builds the workshop projection from committed and durably accepted events. */
    WorkshopOverviewView workshopOverview();
    /** Composes owned stock with market references without leaking ownership into the market context. */
    PageResult<WorkshopPaintView> searchWorkshopPaintPage(SearchMarketPaintsQuery filters,
            boolean manufacturerSheetOnly, boolean realResultOnly, PageQuery page);
    /** Counts filter values for paints currently owned by the workshop. */
    PaintFacetsView workshopPaintFacets(SearchMarketPaintsQuery filters,
            boolean manufacturerSheetOnly, boolean realResultOnly);
    /** Lists painting-project aggregate summaries in deterministic order. */
    List<PaintingProjectView> listPaintingProjects();
    /** Lists physical items, optionally scoped to one painting project. */
    List<WorkshopItemView> listWorkshopItems(String paintingProjectId);
    /** Returns one physical item with its activity or raises not found. */
    WorkshopItemView.Detail getWorkshopItem(String itemId);
    /** Computes the import and missing-paint impact of a market product without mutating state. */
    ProductImportPreviewView previewProductImport(String productId);
    /** Ranks owned-paint substitutions for a market guide and flags manual review. */
    GuideReconciliationView reconcileMarketPaintingGuide(String guideId);
    /** Idempotently imports one market product as one atomic project/item publication. */
    CreatePaintingProjectResult createPaintingProject(CreatePaintingProjectCommand command);
    /** Changes a painting-project lifecycle through its aggregate and returns after durable acceptance. */
    PublicationReceipt transitionPaintingProject(TransitionPaintingProjectCommand command);
    /** Adds one physical copy after validating project and catalog membership. */
    PublicationReceipt addWorkshopItem(AddWorkshopItemCommand command);
    /** Applies an ordered workflow transition decided by the physical-item aggregate. */
    PublicationReceipt transitionStage(TransitionStageCommand command);
    /** Appends an immutable note to a physical item's journal. */
    PublicationReceipt addWorkshopItemComment(AddWorkshopItemCommentCommand command);
    /** Validates, stores and journals one progress photo, deleting media if publication fails. */
    PublicationReceipt addWorkshopItemPhoto(AddWorkshopItemPhotoCommand command);
    /** Lists personal recipe projections, optionally scoped to one catalog item. */
    List<WorkshopRecipeView> listWorkshopRecipes(String catalogItemId);
    /** Creates a draft recipe after validating guide slots and owned paints. */
    PublicationReceipt createWorkshopRecipe(CreateWorkshopRecipeCommand command);
    /** Executes a recipe lifecycle action through the recipe aggregate. */
    PublicationReceipt transitionWorkshopRecipe(TransitionWorkshopRecipeCommand command);
    /** Assigns an active compatible recipe to one physical item. */
    PublicationReceipt assignWorkshopRecipe(AssignWorkshopRecipeCommand command);
    /** Combines calculated paint needs and explicit shopping intentions. */
    List<ShoppingItemView> listShoppingItems();
    /** Changes one shopping intention through its aggregate. */
    PublicationReceipt setShoppingItemStatus(SetShoppingItemStatusCommand command);
    /** Returns newest-first global activity, optionally scoped to one painting project. */
    List<EventEnvelope> listActivity(String paintingProjectId);
}
