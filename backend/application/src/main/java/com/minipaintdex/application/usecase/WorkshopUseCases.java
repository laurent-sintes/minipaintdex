package com.minipaintdex.application.usecase;
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
import com.minipaintdex.application.command.TransitionWorkshopPaintableStageCommand;
import com.minipaintdex.application.command.TransitionWorkshopRecipeCommand;
import com.minipaintdex.application.command.TransitionPaintingProjectCommand;
import com.minipaintdex.application.event.PublicationReceipt;
import com.minipaintdex.application.result.CreatePaintingProjectResult;
import com.minipaintdex.application.result.PageResult;
import com.minipaintdex.application.query.PageQuery;
import com.minipaintdex.application.query.SearchPaintProductsQuery;
import com.minipaintdex.domain.event.EventEnvelope;
import com.minipaintdex.application.view.PaintingProjectView;
import com.minipaintdex.application.view.PaintingProjectImportPreviewView;
import com.minipaintdex.application.view.GuideReconciliationView;
import com.minipaintdex.application.view.ShoppingListEntryView;
import com.minipaintdex.application.view.WorkshopPaintableView;
import com.minipaintdex.application.view.WorkshopOverviewView;
import com.minipaintdex.application.view.WorkshopRecipeView;
import com.minipaintdex.application.view.WorkshopPaintStockView;
import com.minipaintdex.application.view.PaintFacetsView;

import java.util.List;

/** Commands and queries for the owner's workshop aggregates. */
public interface WorkshopUseCases {
    /**
     * Searches results, suggestions, or both from one ranked selection. Results are pageable and
     * explicitly sortable; suggestions stay relevance-ordered, bounded and empty for blank text.
     * Workshop applies ownership before limiting; Market never reads Workshop state.
     * Unrequested parts are null. Invalid include/paging/text/limit raises invalid_input;
     * index failures raise search_unavailable, never a misleading empty result.
     * Each source is read once per call as an immutable generation; concurrent changes appear
     * on later reads. Read-only, no idempotency key, correlation echoed, no resources retained.
     */
    com.minipaintdex.application.result.PaintSearchResult<WorkshopPaintStockView> searchWorkshopPaintStocks(
            com.minipaintdex.application.query.PaintSearchQuery query);

    /** Validates all registrations and merges stable pot IDs atomically; repeats preserve photos and observations. Dry-run never writes. Mutations return after durable acceptance; validation and not-found/conflict errors are transport independent. */
    ImportPaintPotsResult importPaintPots(ImportPaintPotsCommand command);
    /** Registers one physical pot idempotently by its stable identity; conflicting product identity fails. Mutations return after durable acceptance; validation and not-found/conflict errors are transport independent. */
    ImportPaintPotsResult registerPaintPot(RegisterPaintPotCommand command);
    /** Records independent condition and level observations on an owned pot. Mutations return after durable acceptance; validation and not-found/conflict errors are transport independent. */
    PublicationReceipt observePaintPot(ObservePaintPotCommand command);
    /** Records a known opening date once; an absent date is not inferred at registration. Mutations return after durable acceptance; validation and not-found/conflict errors are transport independent. */
    PublicationReceipt openPaintPot(OpenPaintPotCommand command);
    /** Records possession without deleting history or affecting the commercial product. Mutations return after durable acceptance; validation and not-found/conflict errors are transport independent. */
    PublicationReceipt changePaintPotPossession(ChangePaintPotPossessionCommand command);
    /** Appends a personal note without changing stock. Mutations return after durable acceptance; validation and not-found/conflict errors are transport independent. */
    PublicationReceipt addPaintPotNote(AddPaintPotNoteCommand command);
    /** Stores a bounded personal photo and publishes its attachment; duplicate commands do not store another copy. Mutations return after durable acceptance; validation and not-found/conflict errors are transport independent. */
    PublicationReceipt addPaintPotPhoto(AddPaintPotPhotoCommand command);
    /** Returns a committed, immutable page in pot-ID order; removed pots are excluded unless requested. */
    PageResult<PaintPotView> searchPaintPots(SearchPaintPotsQuery query);
    /** Returns one committed pot and its product reference or raises not found; retains no resources. */
    PaintPotView getPaintPot(String paintPotId);
    /** Builds the workshop projection from committed and durably accepted events. */
    WorkshopOverviewView workshopOverview();
    /** Counts filter values for paints currently owned by the workshop. */
    PaintFacetsView workshopPaintStockFacets(SearchPaintProductsQuery filters,
            boolean manufacturerSheetOnly, boolean realResultOnly);
    /** Lists painting-project aggregate summaries in deterministic order. */
    List<PaintingProjectView> listPaintingProjects();
    /** Lists physical items, optionally scoped to one painting project. */
    List<WorkshopPaintableView> listWorkshopPaintables(String paintingProjectId);
    /** Returns one physical item with its activity or raises not found. */
    WorkshopPaintableView.Detail getWorkshopPaintable(String workshopPaintableId);
    /** Computes the import and missing-paint impact of a market product without mutating state. */
    PaintingProjectImportPreviewView previewPaintingProjectImport(String paintableProductId);
    /** Ranks owned-paint substitutions for a market guide and flags manual review. */
    GuideReconciliationView reconcileMarketPaintingGuide(String guideId);
    /** Idempotently imports one market product as one atomic project/item publication. */
    CreatePaintingProjectResult createPaintingProject(CreatePaintingProjectCommand command);
    /** Changes a painting-project lifecycle through its aggregate and returns after durable acceptance. */
    PublicationReceipt transitionPaintingProject(TransitionPaintingProjectCommand command);
    /** Adds one physical copy after validating project and catalog membership. */
    PublicationReceipt addWorkshopPaintable(AddWorkshopPaintableCommand command);
    /** Applies an ordered workflow transition decided by the physical-item aggregate. */
    PublicationReceipt transitionWorkshopPaintableStage(TransitionWorkshopPaintableStageCommand command);
    /** Appends an immutable note to a physical item's journal. */
    PublicationReceipt addWorkshopPaintableComment(AddWorkshopPaintableCommentCommand command);
    /** Validates, stores and journals one progress photo, deleting media if publication fails. */
    PublicationReceipt addWorkshopPaintablePhoto(AddWorkshopPaintablePhotoCommand command);
    /** Lists personal recipe projections, optionally scoped to one paintable component. */
    List<WorkshopRecipeView> listWorkshopRecipes(String paintableComponentId);
    /** Creates a draft recipe after validating guide slots and owned paints. */
    PublicationReceipt createWorkshopRecipe(CreateWorkshopRecipeCommand command);
    /** Executes a recipe lifecycle action through the recipe aggregate. */
    PublicationReceipt transitionWorkshopRecipe(TransitionWorkshopRecipeCommand command);
    /** Assigns an active compatible recipe to one physical item. */
    PublicationReceipt assignWorkshopRecipe(AssignWorkshopRecipeCommand command);
    /** Combines calculated paint needs and explicit shopping intentions. */
    List<ShoppingListEntryView> listShoppingListEntries();
    /** Changes a list entry's checked marker; never purchases paint or changes inventory. */
    PublicationReceipt setShoppingListEntryChecked(SetShoppingListEntryCheckedCommand command);
    /** Returns newest-first global activity, optionally scoped to one painting project. */
    List<EventEnvelope> listActivity(String paintingProjectId);
}
