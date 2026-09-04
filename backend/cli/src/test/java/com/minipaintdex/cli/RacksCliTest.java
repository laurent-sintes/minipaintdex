package com.minipaintdex.cli;
import com.minipaintdex.application.usecase.*;
import com.minipaintdex.application.port.*;
import com.minipaintdex.application.storage.StorageContracts.*;
import com.minipaintdex.application.event.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import picocli.CommandLine;
import java.nio.file.*;
import java.time.Instant;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.*;

class RacksCliTest {
    @TempDir Path temporaryDirectory;
    @Test void exposesRackPagingAndTheSameTypedConfirmationAsRest() throws Exception {
        var workshop = mock(WorkshopUseCases.class);
        var root = new MiniPaintDexCli(mock(MarketCatalogUseCases.class), workshop, mock(AdministrationUseCases.class), mock(EventBus.class), mock(PersistenceLifecycle.class));
        var cli = new CommandLine(root);
        assertEquals(0, cli.execute("--format", "json", "workshop", "racks", "list", "--page", "2", "--size", "5", "--correlation-id", "test"));
        var page = ArgumentCaptor.forClass(ListRacks.class); verify(workshop).listWorkshopRacks(page.capture());
        assertEquals(2, page.getValue().page().page()); assertEquals(5, page.getValue().page().size());
        when(workshop.confirmPaintStorage(any())).thenReturn(new PublicationReceipt("receipt", EventPublicationStatus.COMPLETED, Instant.EPOCH, "test"));
        Path input = temporaryDirectory.resolve("confirm.json");
        Files.writeString(input, "{\"snapshotToken\":\"abc\",\"placements\":[],\"allowEstimates\":true}");
        assertEquals(0, cli.execute("--format", "json", "--server-url", "http://127.0.0.1:1", "workshop", "paint-storage", "confirm",
                "--input", input.toString(), "--idempotency-key", "confirm-one", "--correlation-id", "test"));
        var capture = ArgumentCaptor.forClass(Confirm.class); verify(workshop).confirmPaintStorage(capture.capture());
        assertEquals("abc", capture.getValue().snapshotToken()); assertEquals("confirm-one", capture.getValue().idempotencyKey()); assertTrue(capture.getValue().allowEstimates());
        when(workshop.addWorkshopRacks(any())).thenReturn(new PublicationReceipt("receipt", EventPublicationStatus.COMPLETED, Instant.EPOCH, "test"));
        Path acquisition = temporaryDirectory.resolve("add.json");
        Files.writeString(acquisition, "{\"rackProductId\":\"wood\",\"quantity\":2,\"location\":\"Desk\"}");
        assertEquals(0, cli.execute("--format", "json", "--server-url", "http://127.0.0.1:1", "workshop", "racks", "add",
                "--input", acquisition.toString(), "--idempotency-key", "add-two", "--correlation-id", "test"));
        var added = ArgumentCaptor.forClass(AddRacks.class); verify(workshop).addWorkshopRacks(added.capture());
        assertEquals("wood", added.getValue().rackProductId()); assertEquals(2, added.getValue().quantity());
        assertEquals("add-two", added.getValue().idempotencyKey());
    }
}
