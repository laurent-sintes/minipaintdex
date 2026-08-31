package com.minipaintdex.cli;

import com.minipaintdex.application.MiniPaintDexService;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

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
            var service = mock(MiniPaintDexService.class);
            org.mockito.Mockito.when(service.health()).thenReturn(Map.of("status", "ok", "storage", "files"));
            var exitCode = new CommandLine(new MiniPaintDexCli(service)).execute("--format", "json", "health");
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
        var command = new CommandLine(new MiniPaintDexCli(mock(MiniPaintDexService.class)));

        assertTrue(command.getSubcommands().get("market").getSubcommands().containsKey("guides"));
        assertTrue(command.getSubcommands().get("market").getSubcommands().containsKey("paintable-products"));
        assertTrue(command.getSubcommands().get("market").getSubcommands().get("paintable-products").getSubcommands().containsKey("preview-import"));
        assertTrue(command.getSubcommands().get("workshop").getSubcommands().containsKey("painting-projects"));
        assertTrue(command.getSubcommands().get("workshop").getSubcommands().get("painting-projects").getSubcommands().containsKey("create"));
        assertTrue(command.getSubcommands().get("workshop").getSubcommands().containsKey("recipes"));
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
}
