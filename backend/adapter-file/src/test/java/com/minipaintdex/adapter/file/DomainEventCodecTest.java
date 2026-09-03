package com.minipaintdex.adapter.file;

import com.minipaintdex.domain.event.Actor;
import com.minipaintdex.domain.event.DomainEvent;
import com.minipaintdex.domain.event.EventEnvelope;
import com.minipaintdex.domain.workshop.WorkflowStage;
import com.minipaintdex.domain.workshop.PaintPotEvent.*;
import com.minipaintdex.domain.workshop.*;
import com.minipaintdex.domain.workshop.PaintComponent;
import com.minipaintdex.domain.workshop.PaintingProjectCreated;
import com.minipaintdex.domain.workshop.PaintingProjectRegistered;
import com.minipaintdex.domain.workshop.PaintingProjectStatus;
import com.minipaintdex.domain.workshop.PaintingProjectStatusChanged;
import com.minipaintdex.domain.workshop.RecipeSolution;
import com.minipaintdex.domain.workshop.RecipeSolutionType;
import com.minipaintdex.domain.workshop.ShoppingListEntryCheckedChanged;
import com.minipaintdex.domain.workshop.WorkflowStageCompleted;
import com.minipaintdex.domain.workshop.WorkflowStageReopened;
import com.minipaintdex.domain.workshop.WorkflowStageSkipped;
import com.minipaintdex.domain.workshop.WorkflowStageStarted;
import com.minipaintdex.domain.workshop.WorkshopCreated;
import com.minipaintdex.domain.workshop.WorkshopPaintableAdded;
import com.minipaintdex.domain.workshop.WorkshopPaintableCommentAdded;
import com.minipaintdex.domain.workshop.WorkshopPaintablePhotoAdded;
import com.minipaintdex.domain.workshop.WorkshopPaintableRecipeAssigned;
import com.minipaintdex.domain.workshop.WorkshopRecipeActivated;
import com.minipaintdex.domain.workshop.WorkshopRecipeArchived;
import com.minipaintdex.domain.workshop.WorkshopRecipeCreated;
import com.minipaintdex.domain.workshop.WorkshopRecipeSuperseded;
import com.minipaintdex.domain.workshop.WorkshopRecipeValidated;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DomainEventCodecTest {
    private static final Instant AT = Instant.parse("2026-08-30T10:00:00Z");

    @Test
    void roundTripsEverySupportedDomainEventType() {
        var solution = new RecipeSolution(
                RecipeSolutionType.MIXTURE, "slot-1", null,
                List.of(new PaintComponent("paint-1", 2, "base"), new PaintComponent("paint-2", 1, null)),
                "Mix thoroughly");
        var events = List.<DomainEvent>of(
                new PaintPotRegistered("pot-one", "paint-one", null, AT),
                new PaintPotObserved("pot-one", PaintPotCondition.THICKENED, PaintPotRemainingLevel.LOW, AT),
                new PaintPotOpened("pot-one", AT),
                new PaintPotPossessionChanged("pot-one", PaintPotPossession.GIVEN_AWAY, AT),
                new PaintPotNoteAdded("pot-one", "Personal note", AT),
                new PaintPotPhotoAdded("pot-one", "media-one", "/media/workshop/pot-one/photo.png", "My pot", "photo.png", "image/png", 10, "a".repeat(64), null, AT),
                new PaintPotPhotoAdded("pot-one", "media-one", "/media/workshop/pot-one/photo.png", "My pot", "photo.png", "image/png", 10, "a".repeat(64),
                        new com.minipaintdex.domain.workshop.PaintPotPhotoCutout("media-two", "/media/workshop/pot-one/cutout.png", 20, "b".repeat(64), "test-cutout"), AT),
                new WorkshopCreated("my-workshop", "My workshop", AT),
                new PaintingProjectRegistered("my-workshop", "project-1", AT),
                new PaintingProjectCreated("project-1", "my-workshop", "game", "Paint game", 2, AT),
                new PaintingProjectStatusChanged("project-1", PaintingProjectStatus.ACTIVE, AT),
                new WorkshopPaintableAdded("item-1", "hero", "project-1", "Hero", 1, AT),
                new WorkshopPaintableCommentAdded("item-1", "project-1", "Note", AT),
                new WorkshopPaintablePhotoAdded("item-1", "project-1", "media-1", "/media/1", WorkflowStage.PAINTING,
                        "Progress", "photo.png", "image/png", 3, "0".repeat(64), AT),
                new WorkshopPaintableRecipeAssigned("item-1", "project-1", "recipe-1", 1, AT),
                new WorkflowStageStarted("item-1", "project-1", WorkflowStage.PREPARATION, "Start", AT),
                new WorkflowStageCompleted("item-1", "project-1", WorkflowStage.PREPARATION, null, AT),
                new WorkflowStageSkipped("item-1", "project-1", WorkflowStage.PRIMING, "Pre-primed", AT),
                new WorkflowStageReopened("item-1", "project-1", WorkflowStage.PRIMING, "Repair", AT),
                new WorkshopRecipeCreated("recipe-1", "project-1", "hero", "guide-1", null,
                        "Hero recipe", 1, List.of(solution), AT),
                new WorkshopRecipeValidated("recipe-1", "project-1", AT),
                new WorkshopRecipeActivated("recipe-1", "project-1", AT),
                new WorkshopRecipeSuperseded("recipe-1", "project-1", "recipe-2", AT),
                new WorkshopRecipeArchived("recipe-1", "project-1", "Old", AT),
                new ShoppingListEntryCheckedChanged("buy-1", true, AT));
        var codec = new DomainEventCodec();

        for (var index = 0; index < events.size(); index++) {
            var event = events.get(index);
            var envelope = new EventEnvelope(
                    "event-" + index, 1, index + 1, AT, new Actor("user", "owner"),
                    "correlation", null, "key-" + index, event);
            assertEquals(envelope, codec.decode(codec.encode(envelope)), event.eventType());
        }
    }

    @Test
    void rejectsEnvelopeMetadataThatContradictsTheTypedEvent() {
        var codec = new DomainEventCodec();
        var envelope = new EventEnvelope(
                "event", 1, 1, AT, new Actor("user", "owner"),
                "correlation", null, "key", new WorkshopCreated("my-workshop", "Workshop", AT));
        var encoded = new java.util.LinkedHashMap<>(codec.encode(envelope));
        encoded.put("aggregate_type", "workshop_item");

        assertThrows(FileStorageException.class, () -> codec.decode(encoded));
    }
}
