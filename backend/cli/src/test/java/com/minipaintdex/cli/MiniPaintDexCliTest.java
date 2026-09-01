package com.minipaintdex.cli;

import com.minipaintdex.application.usecase.AdministrationUseCases;
import com.minipaintdex.application.usecase.MarketCatalogUseCases;
import com.minipaintdex.application.usecase.WorkshopUseCases;
import com.minipaintdex.application.port.EventBus;
import com.minipaintdex.application.event.EventBusState;
import com.minipaintdex.application.port.PersistenceLifecycle;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class MiniPaintDexCliTest {
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
        assertTrue(command.getSubcommands().get("market").getSubcommands().get("paints").getSubcommands().containsKey("model"));
        assertTrue(command.getSubcommands().get("workshop").getSubcommands().containsKey("painting-projects"));
        assertTrue(command.getSubcommands().get("workshop").getSubcommands().get("painting-projects").getSubcommands().containsKey("preview-import"));
        assertTrue(command.getSubcommands().get("workshop").getSubcommands().get("painting-projects").getSubcommands().containsKey("create"));
        assertTrue(command.getSubcommands().get("workshop").getSubcommands().get("painting-projects").getSubcommands().containsKey("transition"));
        assertTrue(command.getSubcommands().get("workshop").getSubcommands().containsKey("recipes"));
        assertTrue(command.getSubcommands().get("workshop").getSubcommands().get("recipes").getSubcommands().containsKey("reconcile-guide"));
        assertTrue(command.getSubcommands().get("workshop").getSubcommands().get("recipes").getSubcommands().containsKey("assign"));
        assertTrue(command.getSubcommands().get("workshop").getSubcommands().get("items").getSubcommands().containsKey("photo"));
        assertTrue(command.getSubcommands().containsKey("shopping"));
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

        assertEquals(0, command.execute("market", "paints", "apply", "--help"));
        assertEquals(0, command.execute("market", "paintable-products", "apply", "--help"));
    }
}
