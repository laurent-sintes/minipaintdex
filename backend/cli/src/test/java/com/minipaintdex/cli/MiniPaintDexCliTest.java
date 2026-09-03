package com.minipaintdex.cli;

import com.minipaintdex.application.usecase.AdministrationUseCases;
import com.minipaintdex.application.usecase.MarketCatalogUseCases;
import com.minipaintdex.application.usecase.WorkshopUseCases;
import com.minipaintdex.application.port.EventBus;
import com.minipaintdex.application.event.EventBusState;
import com.minipaintdex.application.port.PersistenceLifecycle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class MiniPaintDexCliTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void readsOneWorkshopPaintStockWithCanonicalIdentityAndCorrelation() {
        var workshop = mock(WorkshopUseCases.class);
        var command = new CommandLine(new MiniPaintDexCli(mock(MarketCatalogUseCases.class), workshop,
                mock(AdministrationUseCases.class), mock(EventBus.class), mock(PersistenceLifecycle.class)));
        assertEquals(0, command.execute("--format", "json", "workshop", "paint-stocks", "show",
                "--paint-product-id", "paint-one", "--correlation-id", "photo-read"));
        var capture = org.mockito.ArgumentCaptor.forClass(com.minipaintdex.application.query.GetWorkshopPaintStockQuery.class);
        org.mockito.Mockito.verify(workshop).getWorkshopPaintStock(capture.capture());
        assertEquals("paint-one", capture.getValue().paintProductId());
        assertEquals("photo-read", capture.getValue().correlationId());
    }

    @Test
    void exposesUsageGuideLanguageScopeAndCorrelation() {
        var market = mock(MarketCatalogUseCases.class);
        var command = new CommandLine(new MiniPaintDexCli(market, mock(WorkshopUseCases.class),
                mock(AdministrationUseCases.class), mock(EventBus.class), mock(PersistenceLifecycle.class)));
        assertEquals(0, command.execute("--format", "json", "market", "paint-usage-guides", "list",
                "--paint-product-id", "paint", "--language", "original", "--size", "2", "--correlation-id", "test"));
        var capture = org.mockito.ArgumentCaptor.forClass(com.minipaintdex.application.query.SearchPaintUsageGuidesQuery.class);
        org.mockito.Mockito.verify(market).searchPaintUsageGuides(capture.capture());
        assertEquals("paint", capture.getValue().paintProductId()); assertEquals("original", capture.getValue().language());
        assertEquals(2, capture.getValue().page().size()); assertEquals("test", capture.getValue().correlationId());
        assertEquals(0, command.execute("--format", "json", "market", "paint-usage-guides", "show", "--paint-usage-guide-id", "guide"));
        org.mockito.Mockito.verify(market).getPaintUsageGuide(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void exposesBothSearchContextsWithEquivalentSuggestionSelection() {
        var market = mock(MarketCatalogUseCases.class);
        var workshop = mock(WorkshopUseCases.class);
        var suggestions = List.of(new com.minipaintdex.application.view.PaintProductSuggestion("karak", "Karak Stone", "Citadel", "Layer", "22-17", "", ""));
        org.mockito.Mockito.when(market.searchPaintProducts(org.mockito.ArgumentMatchers.any(com.minipaintdex.application.query.PaintSearchQuery.class)))
                .thenReturn(new com.minipaintdex.application.result.PaintSearchResult<>(null, suggestions, "test"));
        org.mockito.Mockito.when(workshop.searchWorkshopPaintStocks(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.minipaintdex.application.result.PaintSearchResult<>(null, suggestions, "test"));
        var command = new CommandLine(new MiniPaintDexCli(market, workshop,
                mock(AdministrationUseCases.class), mock(EventBus.class), mock(PersistenceLifecycle.class)));
        var output = new ByteArrayOutputStream();
        var previous = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            for (var context : List.of("market", "workshop")) {
                assertEquals(0, command.execute("--format", "json", context, context.equals("market") ? "paint-products" : "paint-stocks",
                        "search", "--include", "suggestions", "--query", "kar", "--suggestion-limit", "3", "--brand", "A", "--brand", "B",
                        "--range", "Citadel::Layer", "--correlation-id", "test"));
            }
        } finally { System.setOut(previous); }
        var captor = org.mockito.ArgumentCaptor.forClass(com.minipaintdex.application.query.PaintSearchQuery.class);
        org.mockito.Mockito.verify(market).searchPaintProducts(captor.capture());
        assertEquals(3, captor.getValue().suggestionLimit());
        assertEquals(java.util.Set.of("suggestions"), captor.getValue().include());
        assertEquals(List.of("A", "B"), captor.getValue().filters().brand());
        org.mockito.Mockito.verify(workshop).searchWorkshopPaintStocks(captor.capture());
        assertEquals("test", captor.getValue().correlationId());
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("\"suggestions\":[{\"paintProductId\":\"karak\""));
    }

    @Test
    void exposesCombinedSearchWithoutLegacySuggestCommands() {
        var market = mock(MarketCatalogUseCases.class);
        var command = new CommandLine(new MiniPaintDexCli(market, mock(WorkshopUseCases.class),
                mock(AdministrationUseCases.class), mock(EventBus.class), mock(PersistenceLifecycle.class)));
        assertEquals(0, command.execute("--format", "json", "market", "paint-products", "search",
                "--include", "results,suggestions", "--page", "2", "--size", "5", "--sort", "name,desc"));
        var capture = org.mockito.ArgumentCaptor.forClass(com.minipaintdex.application.query.PaintSearchQuery.class);
        org.mockito.Mockito.verify(market).searchPaintProducts(capture.capture());
        assertEquals(java.util.Set.of("results", "suggestions"), capture.getValue().include());
        assertEquals(2, capture.getValue().page().page());
        assertTrue(!command.getSubcommands().get("market").getSubcommands().get("paint-products").getSubcommands().containsKey("suggest"));
        assertTrue(!command.getSubcommands().get("workshop").getSubcommands().get("paint-stocks").getSubcommands().containsKey("suggest"));
    }

    @Test
    void exposesPhysicalPotCommandsAndForwardsObservations() {
        var workshop = mock(WorkshopUseCases.class);
        org.mockito.Mockito.when(workshop.observePaintPot(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.minipaintdex.application.event.PublicationReceipt("p", com.minipaintdex.application.event.EventPublicationStatus.COMPLETED, Instant.now(), "test"));
        var command = new CommandLine(new MiniPaintDexCli(mock(MarketCatalogUseCases.class), workshop,
                mock(AdministrationUseCases.class), mock(EventBus.class), mock(PersistenceLifecycle.class)));
        assertEquals(java.util.Set.of("search", "show", "add", "import", "observe", "open", "set-possession", "note", "photo", "photo-preview"),
                command.getSubcommands().get("workshop").getSubcommands().get("paint-pots").getSubcommands().keySet());
        assertEquals(0, command.execute("--server-url", "http://127.0.0.1:0", "--format", "json", "workshop", "paint-pots", "observe",
                "--paint-pot-id", "pot-one", "--condition", "thickened", "--remaining-level", "low", "--idempotency-key", "observation"));
        var captor = org.mockito.ArgumentCaptor.forClass(com.minipaintdex.application.command.ObservePaintPotCommand.class);
        org.mockito.Mockito.verify(workshop).observePaintPot(captor.capture());
        assertEquals("pot-one", captor.getValue().paintPotId());
        assertEquals("low", captor.getValue().remainingLevel());
    }

    @Test
    void exposesPhotoPreviewAndBackgroundRemovalThroughTheSameWorkshopPort() throws Exception {
        var directory = java.nio.file.Files.createTempDirectory(java.nio.file.Files.createDirectories(java.nio.file.Path.of("target")), "photo-cli-");
        var file = directory.resolve("pot.png");
        var output = directory.resolve("preview.png");
        java.nio.file.Files.write(file, new byte[]{1, 2, 3});
        var workshop = mock(WorkshopUseCases.class);
        org.mockito.Mockito.when(workshop.previewPaintPotPhoto(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.minipaintdex.application.result.PaintPotPhotoPreview(new byte[]{4, 5, 6}, "test-cutout", "preview"));
        org.mockito.Mockito.when(workshop.addPaintPotPhoto(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.minipaintdex.application.event.PublicationReceipt("photo", com.minipaintdex.application.event.EventPublicationStatus.COMPLETED, Instant.now(), "photo"));
        var command = new CommandLine(new MiniPaintDexCli(mock(MarketCatalogUseCases.class), workshop,
                mock(AdministrationUseCases.class), mock(EventBus.class), mock(PersistenceLifecycle.class)));
        assertEquals(0, command.execute("--format", "json", "workshop", "paint-pots", "photo-preview", "--paint-pot-id", "pot-one",
                "--file", file.toString(), "--output", output.toString(), "--correlation-id", "preview"));
        org.junit.jupiter.api.Assertions.assertArrayEquals(new byte[]{4, 5, 6}, java.nio.file.Files.readAllBytes(output));
        var capture = org.mockito.ArgumentCaptor.forClass(com.minipaintdex.application.query.PreviewPaintPotPhotoQuery.class);
        org.mockito.Mockito.verify(workshop).previewPaintPotPhoto(capture.capture());
        assertEquals("pot-one", capture.getValue().paintPotId());
        assertEquals("preview", capture.getValue().correlationId());
        assertEquals(0, command.execute("--server-url", "http://127.0.0.1:0", "--format", "json", "workshop", "paint-pots", "photo",
                "--paint-pot-id", "pot-one", "--file", file.toString(), "--remove-background", "--idempotency-key", "photo-key"));
        var upload = org.mockito.ArgumentCaptor.forClass(com.minipaintdex.application.command.AddPaintPotPhotoCommand.class);
        org.mockito.Mockito.verify(workshop).addPaintPotPhoto(upload.capture());
        assertTrue(upload.getValue().removeBackground());
        assertEquals("photo-key", upload.getValue().idempotencyKey());
    }

    @Test
    void shoppingCheckedOptionRequiresAnExplicitBooleanValue() {
        var command = new CommandLine(new MiniPaintDexCli(mock(MarketCatalogUseCases.class), mock(WorkshopUseCases.class),
                mock(AdministrationUseCases.class), mock(EventBus.class), mock(PersistenceLifecycle.class)));
        for (var value : List.of("true", "false")) {
            var parsed = command.parseArgs("workshop", "shopping-list", "entries", "set-checked",
                    "--shopping-list-entry-id", "buy-1", "--checked", value);
            while (parsed.hasSubcommand()) parsed = parsed.subcommand();
            assertEquals(Boolean.valueOf(value), parsed.matchedOptionValue("--checked", null));
        }
        org.junit.jupiter.api.Assertions.assertThrows(CommandLine.MissingParameterException.class,
                () -> command.parseArgs("workshop", "shopping-list", "entries", "set-checked",
                        "--shopping-list-entry-id", "buy-1", "--checked"));
    }

    @Test
    void exposesAlignedWorkshopStockAndShoppingQueries() {
        var workshop = mock(WorkshopUseCases.class);
        org.mockito.Mockito.when(workshop.searchWorkshopPaintStocks(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.minipaintdex.application.result.PaintSearchResult<>(new com.minipaintdex.application.result.PageResult<>(List.of(), 1, 5, 0), null, "test"));
        org.mockito.Mockito.when(workshop.workshopPaintStockFacets(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(new com.minipaintdex.application.view.PaintFacetsView(0, List.of()));
        org.mockito.Mockito.when(workshop.listShoppingListEntries()).thenReturn(List.of());
        var command = new CommandLine(new MiniPaintDexCli(mock(MarketCatalogUseCases.class), workshop,
                mock(AdministrationUseCases.class), mock(EventBus.class), mock(PersistenceLifecycle.class)));
        var output = new ByteArrayOutputStream();
        var previous = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            assertEquals(0, command.execute("--format", "json", "workshop", "paint-stocks", "search",
                    "--brand", "Vallejo", "--range", "Warhammer Colour::Contrast", "--color", "blue", "--color", "red",
                    "--page", "1", "--size", "5", "--sort", "name,desc", "--manufacturer-sheet-only"));
            assertEquals(0, command.execute("--format", "json", "workshop", "paint-stocks", "facets", "--brand", "Vallejo"));
            assertEquals(0, command.execute("--format", "json", "workshop", "shopping-list", "entries", "list"));
        } finally {
            System.setOut(previous);
        }
        var capture = org.mockito.ArgumentCaptor.forClass(com.minipaintdex.application.query.PaintSearchQuery.class);
        org.mockito.Mockito.verify(workshop).searchWorkshopPaintStocks(capture.capture());
        var filters = capture.getValue().filters();
        var page = capture.getValue().page();
        assertTrue(capture.getValue().manufacturerSheetOnly());
        assertEquals(List.of("blue", "red"), filters.color());
        assertEquals(1, page.page());
        assertEquals(5, page.size());
        assertEquals(com.minipaintdex.application.query.SortOrder.Direction.DESCENDING, page.sort().getFirst().direction());
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("\"content\":[]"));
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("\"entries\":[]"));
        assertTrue(!command.getSubcommands().containsKey("shopping"));
        var workshopCommands = command.getSubcommands().get("workshop").getSubcommands();
        assertTrue(!workshopCommands.containsKey("items"));
        assertTrue(workshopCommands.get("paintables").getSubcommands().get("add").getCommandSpec()
                .findOption("--paintable-component-id") != null);
    }

    @Test
    void forwardsCatalogEditionQueriesAndReadsEditionImports() throws Exception {
        var market = mock(MarketCatalogUseCases.class);
        org.mockito.Mockito.when(market.searchPaintCatalogEditions(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.minipaintdex.application.result.PageResult<>(List.of(), 2, 5, 0));
        var root = new MiniPaintDexCli(market, mock(WorkshopUseCases.class), mock(AdministrationUseCases.class),
                mock(EventBus.class), mock(PersistenceLifecycle.class));
        assertEquals(0, new CommandLine(root).execute("--format", "json", "market", "paint-catalog-editions", "list",
                "--brand", "Brand", "--page", "2", "--size", "5", "--correlation-id", "test"));
        var query = org.mockito.ArgumentCaptor.forClass(com.minipaintdex.application.query.SearchPaintCatalogEditionsQuery.class);
        org.mockito.Mockito.verify(market).searchPaintCatalogEditions(query.capture());
        assertEquals(2, query.getValue().page().page());
        assertEquals("Brand", query.getValue().brand());
        var input = temporaryDirectory.resolve("editions.json");
        Files.writeString(input, "{\"schema_version\":1,\"kind\":\"market_paints\",\"operations\":[],\"catalog_editions\":[{\"id\":\"brand-2019\"}]}");
        assertEquals(1, root.readPaintChangeSet(input, true).catalogEditions().size());
    }

    @Test
    void forwardsRepeatedPaintFiltersAndQualifiedRangesToTheSharedQuery() {
        var market = mock(MarketCatalogUseCases.class);
        var command = new CommandLine(new MiniPaintDexCli(market, mock(WorkshopUseCases.class),
                mock(AdministrationUseCases.class), mock(EventBus.class), mock(PersistenceLifecycle.class)));
        org.mockito.Mockito.when(market.searchPaintProducts(org.mockito.ArgumentMatchers.any(com.minipaintdex.application.query.PaintSearchQuery.class)))
                .thenReturn(new com.minipaintdex.application.result.PaintSearchResult<>(new com.minipaintdex.application.result.PageResult<>(List.of(), 0, 60, 0), null, "test"));
        assertEquals(0, command.execute("--format", "json", "market", "paint-products", "search",
                "--brand", "Vallejo", "--brand", "AK Interactive", "--range", "Warhammer Colour::Contrast",
                "--color", "blue", "--color", "red"));
        var query = org.mockito.ArgumentCaptor.forClass(com.minipaintdex.application.query.PaintSearchQuery.class);
        org.mockito.Mockito.verify(market).searchPaintProducts(query.capture());
        assertEquals(List.of("Vallejo", "AK Interactive"), query.getValue().filters().brand());
        assertEquals(List.of("blue", "red"), query.getValue().filters().color());
        assertEquals("Warhammer Colour::Contrast", query.getValue().filters().range().getFirst().selectionKey());
    }

    @Test
    void healthHasDeterministicJsonOutputAndExitCode() {
        var output = new ByteArrayOutputStream();
        var previous = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            var market = mock(MarketCatalogUseCases.class);
            var workshop = mock(WorkshopUseCases.class);
            var administration = mock(AdministrationUseCases.class);
            var eventBus = mock(EventBus.class);
            var persistence = mock(PersistenceLifecycle.class);
            var now = Instant.parse("2026-08-30T10:00:00Z");
            org.mockito.Mockito.when(eventBus.state()).thenReturn(new EventBusState(true, true, 0, 0));
            org.mockito.Mockito.when(persistence.status()).thenReturn(new PersistenceLifecycle.PersistenceStatus(
                    "ready", "files", 1, "fixture", now, now, now, null));
            var exitCode = new CommandLine(new MiniPaintDexCli(
                    market, workshop, administration, eventBus, persistence))
                    .execute("--format", "json", "health");
            assertEquals(0, exitCode);
        } finally {
            System.setOut(previous);
        }
        var json = output.toString(StandardCharsets.UTF_8);
        assertTrue(json.contains("\"status\":\"ok\""));
        assertTrue(json.contains("\"storage\":\"files\""));
    }

    @Test
    void exposesMarketGuideAndWorkshopRecipeUseCases() {
        var command = new CommandLine(new MiniPaintDexCli(
                mock(MarketCatalogUseCases.class), mock(WorkshopUseCases.class),
                mock(AdministrationUseCases.class), mock(EventBus.class), mock(PersistenceLifecycle.class)));

        assertTrue(command.getSubcommands().get("market").getSubcommands().containsKey("guides"));
        assertTrue(command.getSubcommands().get("market").getSubcommands().containsKey("paintable-products"));
        assertTrue(command.getSubcommands().get("market").getSubcommands().get("paint-products").getSubcommands().containsKey("model"));
        assertTrue(command.getSubcommands().get("workshop").getSubcommands().containsKey("painting-projects"));
        assertTrue(command.getSubcommands().get("workshop").getSubcommands().get("painting-projects").getSubcommands().containsKey("preview-import"));
        assertTrue(command.getSubcommands().get("workshop").getSubcommands().get("painting-projects").getSubcommands().containsKey("create"));
        assertTrue(command.getSubcommands().get("workshop").getSubcommands().get("painting-projects").getSubcommands().containsKey("transition"));
        assertTrue(command.getSubcommands().get("workshop").getSubcommands().containsKey("recipes"));
        assertTrue(command.getSubcommands().get("workshop").getSubcommands().get("recipes").getSubcommands().containsKey("reconcile-guide"));
        assertTrue(command.getSubcommands().get("workshop").getSubcommands().get("recipes").getSubcommands().containsKey("assign"));
        assertTrue(command.getSubcommands().get("workshop").getSubcommands().get("paintables").getSubcommands().containsKey("photo"));
        assertTrue(command.getSubcommands().get("workshop").getSubcommands().containsKey("shopping-list"));
        assertTrue(command.getSubcommands().get("datasets").getSubcommands().containsKey("import"));
    }

    @Test
    void keepsSpringConfigurationArgumentsOutOfPicocliParsing() {
        var arguments = MiniPaintDexCli.cliArguments(new String[]{
                "--root", ".", "--minipaintdex.paint-matching.candidate-limit=2", "health"});

        assertEquals(List.of("--root", ".", "health"), List.of(arguments));
    }

    @Test
    void nestedMarketCommandsExposeHelpWithoutRequiringBusinessArguments() {
        var command = new CommandLine(new MiniPaintDexCli(
                mock(MarketCatalogUseCases.class), mock(WorkshopUseCases.class),
                mock(AdministrationUseCases.class), mock(EventBus.class), mock(PersistenceLifecycle.class)));

        assertEquals(0, command.execute("market", "paint-products", "apply", "--help"));
        assertEquals(0, command.execute("market", "paintable-products", "apply", "--help"));
    }

    @Test
    void paintChangeSetReaderPreservesJsonFieldOrder() throws Exception {
        var path = temporaryDirectory.resolve("paint-change-set.json");
        Files.writeString(path, """
                {
                  "schema_version": 1,
                  "kind": "market_paints",
                  "operations": [{
                    "action": "upsert",
                    "record": {
                      "lifecycle_status": "active",
                      "name": "Ordered paint",
                      "id": "tst-1",
                      "manufacturer_image": {
                        "credit": "Official",
                        "image_quality": "official_photo",
                        "path": "/media/paint.webp"
                      }
                    }
                  }]
                }
                """);
        var cli = new MiniPaintDexCli(
                mock(MarketCatalogUseCases.class), mock(WorkshopUseCases.class),
                mock(AdministrationUseCases.class), mock(EventBus.class), mock(PersistenceLifecycle.class));

        var command = cli.readPaintChangeSet(path, true);
        var record = command.operations().getFirst().record();
        assertEquals(
                List.of("lifecycle_status", "name", "id", "manufacturer_image"),
                record.fields().stream().map(field -> field.name()).toList());
        var image = (com.minipaintdex.application.document.StructuredDocument.ObjectValue)
                record.fields().getLast().value();
        assertEquals(
                List.of("credit", "image_quality", "path"),
                image.value().fields().stream().map(field -> field.name()).toList());
    }

    @Test
    void paintChangeSetReaderPreservesExplicitQualityDecisions() throws Exception {
        var path = temporaryDirectory.resolve("quality-review.json");
        Files.writeString(path, """
                {"schema_version":1,"kind":"market_paints","operations":[{"action":"upsert","record":{
                "id":"tst-1","source_snapshots":[{"provider":"reviewed-paint-color-quality",
                "url":"https://example.test/chart","payload":{"field":"profile.finish","before":"unknown",
                "after":"satin","review_id":"review-1","manifest_sha256":"abc","rationale":"Exact chart"}}]}}]}
                """);
        var cli = new MiniPaintDexCli(mock(MarketCatalogUseCases.class), mock(WorkshopUseCases.class),
                mock(AdministrationUseCases.class), mock(EventBus.class), mock(PersistenceLifecycle.class));
        var command = cli.readPaintChangeSet(path, true);
        var record = com.minipaintdex.application.validation.StructuredDocuments.toMap(command.operations().getFirst().record());
        var sources = com.minipaintdex.application.validation.StructuredDocuments.maps(record.get("source_snapshots"));
        var payload = com.minipaintdex.application.validation.StructuredDocuments.map(sources.getFirst().get("payload"));
        assertEquals("profile.finish", payload.get("field"));
        assertEquals("unknown", payload.get("before"));
        assertEquals("satin", payload.get("after"));
    }
}
