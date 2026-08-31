package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.shared.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AggregateInvariantTest {
    private static final Instant AT = Instant.parse("2026-08-31T10:00:00Z");

    @Test
    void workshopUsesItsCanonicalIdentityAndRejectsDuplicateProjectHistory() {
        assertThrows(DomainException.class, () -> Workshop.create("another-workshop", "Mine", AT));
        assertThrows(DomainException.class, () -> Workshop.rehydrate(List.of()));
        assertThrows(DomainException.class, () -> Workshop.rehydrate(List.of(
                new WorkshopCreated(Workshop.DEFAULT_ID, "Mine", AT),
                new PaintingProjectRegistered(Workshop.DEFAULT_ID, "paint-project", AT.plusSeconds(1)),
                new PaintingProjectRegistered(Workshop.DEFAULT_ID, "paint-project", AT.plusSeconds(2)))));
    }

    @Test
    void rehydrationRejectsAggregateIdentityChangesAndIllegalProjectTransitions() {
        assertThrows(DomainException.class, () -> WorkshopItem.rehydrate(List.of(
                new WorkshopItemAdded("item-one", "catalog-item", "paint-project", "One", 1, AT),
                new WorkshopItemCommentAdded("item-two", "paint-project", "wrong aggregate", AT.plusSeconds(1)))));

        assertThrows(DomainException.class, () -> PaintingProject.rehydrate(List.of(
                new PaintingProjectCreated("paint-project", Workshop.DEFAULT_ID, "paintable-product", "Project", 1, AT),
                new PaintingProjectStatusChanged("paint-project", PaintingProjectStatus.COMPLETED, AT.plusSeconds(1)))));
    }

    @Test
    void recipeHistoryUsesTheSameLifecycleRulesAsCommands() {
        var solution = new RecipeSolution(
                RecipeSolutionType.SINGLE_PAINT, "armor", "paint-one", List.of(), null);
        var created = new WorkshopRecipeCreated(
                "recipe-one", "paint-project", "catalog-item", null, null,
                "Recipe", 1, List.of(solution), AT);

        assertThrows(DomainException.class, () -> WorkshopRecipe.rehydrate(List.of(
                created, new WorkshopRecipeActivated("recipe-one", "paint-project", AT.plusSeconds(1)))));
    }

    @Test
    void recipeSolutionsEnforceTheirExactShape() {
        assertThrows(DomainException.class, () -> new RecipeSolution(
                RecipeSolutionType.TECHNIQUE, null, "paint-one", List.of(), "Stipple"));
        assertThrows(DomainException.class, () -> new RecipeSolution(
                RecipeSolutionType.MIXTURE, null, null,
                List.of(new PaintComponent("paint-one", 1, null),
                        new PaintComponent("paint-one", 2, null)), null));
    }

    @Test
    void onlyExplicitlyOptionalWorkflowStagesCanBeSkipped() {
        var item = WorkshopItem.create("item-one", "catalog-item", "paint-project", "One", 1, AT);
        assertThrows(DomainException.class, () -> item.transition(
                WorkflowStage.PREPARATION, StageAction.SKIP, "Already clean", AT.plusSeconds(1)));

        item.transition(WorkflowStage.PREPARATION, StageAction.COMPLETE, null, AT.plusSeconds(1));
        item.transition(WorkflowStage.PRIMING, StageAction.COMPLETE, null, AT.plusSeconds(2));
        item.transition(WorkflowStage.PRE_HIGHLIGHT, StageAction.SKIP, "Not using a fast-paint method", AT.plusSeconds(3));

        assertEquals(WorkflowStageStatus.SKIPPED, item.workflow().get(WorkflowStage.PRE_HIGHLIGHT));
    }

    @Test
    void progressPhotoMetadataIsStructurallyValidated() {
        assertThrows(DomainException.class, () -> new WorkshopItemPhotoAdded(
                "item-one", "paint-project", "media-one", "/media/one", WorkflowStage.PAINTING,
                "Progress", "photo.png", "image/png", 12, "not-a-sha", AT));
    }

    @Test
    void workshopReferenceFilesUseTypedOwnerModels() {
        assertThrows(DomainException.class, () -> new WorkshopPaintInventory(List.of(
                new WorkshopPaintInventory.Stock("paint-one", 1),
                new WorkshopPaintInventory.Stock("paint-one", 2))));
        assertThrows(DomainException.class, () -> new WorkshopShoppingPlan(List.of(
                new WorkshopShoppingPlan.Intent(
                        "buy-one", null, null, null, null, null, null,
                        WorkshopShoppingPlan.Priority.LOW))));
    }
}
