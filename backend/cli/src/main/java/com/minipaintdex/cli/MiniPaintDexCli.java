package com.minipaintdex.cli;

import com.minipaintdex.adapter.file.FileMiniPaintDexRepository;
import com.minipaintdex.application.MiniPaintDexService;
import com.minipaintdex.application.command.AddWorkshopItemCommand;
import com.minipaintdex.application.command.TransitionStageCommand;
import com.minipaintdex.domain.workflow.DomainException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(
        name = "minipaintdex",
        mixinStandardHelpOptions = true,
        description = "MiniPaintDex local workshop CLI",
        subcommands = {
                MiniPaintDexCli.Health.class,
                MiniPaintDexCli.Bootstrap.class,
                MiniPaintDexCli.Market.class,
                MiniPaintDexCli.Workshop.class,
                MiniPaintDexCli.Activity.class,
                MiniPaintDexCli.Projections.class,
                MiniPaintDexCli.Exports.class
        })
public final class MiniPaintDexCli implements Runnable {
    @Option(names = "--root", description = "MiniPaintDex repository root", defaultValue = ".")
    Path root;

    @Option(names = "--format", description = "Output format: human or json", defaultValue = "human")
    String format;

    private final JsonMapper json = JsonMapper.builder().build();
    private MiniPaintDexService service;

    public static void main(String[] args) {
        var root = new MiniPaintDexCli();
        var commandLine = new CommandLine(root);
        commandLine.setExecutionExceptionHandler((exception, command, parseResult) -> {
            if (exception instanceof DomainException domain) {
                root.error(domain.code(), domain.getMessage());
                return switch (domain.code()) {
                    case "not_found" -> 4;
                    case "conflict", "invalid_transition" -> 5;
                    default -> 2;
                };
            }
            exception.printStackTrace(command.getErr());
            return 1;
        });
        System.exit(commandLine.execute(args));
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    MiniPaintDexService service() {
        if (service == null) {
            var repository = new FileMiniPaintDexRepository(root);
            service = new MiniPaintDexService(repository, repository);
        }
        return service;
    }

    void output(Object value) {
        System.out.println(json.writeValueAsString(value));
    }

    void error(String code, String message) {
        System.err.println(json.writeValueAsString(Map.of("error", Map.of("code", code, "message", message))));
    }

    @Command(name = "health", description = "Check the local application")
    static final class Health implements Callable<Integer> {
        @ParentCommand MiniPaintDexCli root;
        public Integer call() { root.output(Map.of("status", "ok", "service", "minipaintdex", "storage", "files")); return 0; }
    }

    @Command(name = "bootstrap", description = "Read the complete initial application view")
    static final class Bootstrap implements Callable<Integer> {
        @ParentCommand MiniPaintDexCli root;
        public Integer call() { root.output(root.service().bootstrap()); return 0; }
    }

    @Command(name = "market", subcommands = {Market.Paints.class, Market.Games.class})
    static final class Market implements Runnable {
        @ParentCommand MiniPaintDexCli root;
        public void run() { CommandLine.usage(this, System.out); }

        @Command(name = "paints", subcommands = Paints.Search.class)
        static final class Paints implements Runnable {
            @ParentCommand Market parent;
            public void run() { CommandLine.usage(this, System.out); }

            @Command(name = "search", description = "Search the market paint catalog")
            static final class Search implements Callable<Integer> {
                @ParentCommand Paints parent;
                @Option(names = "--query") String query;
                @Option(names = "--brand") String brand;
                @Option(names = "--range") String range;
                @Option(names = "--type") String type;
                @Option(names = "--color") String color;
                public Integer call() {
                    var filters = new LinkedHashMap<String, String>();
                    if (query != null) filters.put("query", query);
                    if (brand != null) filters.put("brand", brand);
                    if (range != null) filters.put("range", range);
                    if (type != null) filters.put("type", type);
                    if (color != null) filters.put("color", color);
                    var root = parent.parent.root;
                    root.output(Map.of("paints", root.service().searchMarketPaints(filters)));
                    return 0;
                }
            }
        }

        @Command(name = "games", subcommands = Games.ListGames.class)
        static final class Games implements Runnable {
            @ParentCommand Market parent;
            public void run() { CommandLine.usage(this, System.out); }
            @Command(name = "list")
            static final class ListGames implements Callable<Integer> {
                @ParentCommand Games parent;
                public Integer call() { var root = parent.parent.root; root.output(Map.of("games", root.service().listProjects())); return 0; }
            }
        }
    }

    @Command(name = "workshop", subcommands = {Workshop.Projects.class, Workshop.Items.class, Workshop.Stage.class})
    static final class Workshop implements Runnable {
        @ParentCommand MiniPaintDexCli root;
        public void run() { CommandLine.usage(this, System.out); }

        @Command(name = "projects", subcommands = Projects.ListProjects.class)
        static final class Projects implements Runnable {
            @ParentCommand Workshop parent;
            public void run() { CommandLine.usage(this, System.out); }
            @Command(name = "list")
            static final class ListProjects implements Callable<Integer> {
                @ParentCommand Projects parent;
                public Integer call() { var root = parent.parent.root; root.output(Map.of("projects", root.service().listProjects())); return 0; }
            }
        }

        @Command(name = "items", subcommands = {Items.ListItems.class, Items.AddItem.class})
        static final class Items implements Runnable {
            @ParentCommand Workshop parent;
            public void run() { CommandLine.usage(this, System.out); }
            @Command(name = "list")
            static final class ListItems implements Callable<Integer> {
                @ParentCommand Items parent;
                @Option(names = "--project") String project;
                public Integer call() { var root = parent.parent.root; root.output(Map.of("items", root.service().listWorkshopItems(project))); return 0; }
            }
            @Command(name = "add")
            static final class AddItem implements Callable<Integer> {
                @ParentCommand Items parent;
                @Option(names = "--item-id") String itemId;
                @Option(names = "--catalog-item", required = true) String catalogItem;
                @Option(names = "--project", required = true) String project;
                @Option(names = "--name", required = true) String name;
                @Option(names = "--actor") String actor;
                @Option(names = "--occurred-at") Instant occurredAt;
                @Option(names = "--correlation-id") String correlationId;
                @Option(names = "--idempotency-key") String idempotencyKey;
                public Integer call() {
                    var root = parent.parent.root;
                    root.output(Map.of("event", root.service().addWorkshopItem(new AddWorkshopItemCommand(itemId, catalogItem, project, name, actor, occurredAt, correlationId, idempotencyKey))));
                    return 0;
                }
            }
        }

        @Command(name = "stage", subcommands = Stage.Transition.class)
        static final class Stage implements Runnable {
            @ParentCommand Workshop parent;
            public void run() { CommandLine.usage(this, System.out); }
            @Command(name = "transition")
            static final class Transition implements Callable<Integer> {
                @ParentCommand Stage parent;
                @Option(names = "--item", required = true) String item;
                @Option(names = "--stage", required = true) String stage;
                @Option(names = "--action", required = true) String action;
                @Option(names = "--comment") String comment;
                @Option(names = "--reason") String reason;
                @Option(names = "--actor") String actor;
                @Option(names = "--occurred-at") Instant occurredAt;
                @Option(names = "--correlation-id") String correlationId;
                @Option(names = "--idempotency-key") String idempotencyKey;
                public Integer call() {
                    var root = parent.parent.root;
                    root.output(Map.of("event", root.service().transitionStage(new TransitionStageCommand(item, stage, action, comment, reason, actor, occurredAt, correlationId, idempotencyKey))));
                    return 0;
                }
            }
        }
    }

    @Command(name = "activity", subcommands = Activity.ListActivity.class)
    static final class Activity implements Runnable {
        @ParentCommand MiniPaintDexCli root;
        public void run() { CommandLine.usage(this, System.out); }
        @Command(name = "list")
        static final class ListActivity implements Callable<Integer> {
            @ParentCommand Activity parent;
            @Option(names = "--project") String project;
            public Integer call() { parent.root.output(Map.of("events", parent.root.service().listActivity(project))); return 0; }
        }
    }

    @Command(name = "projections", subcommands = Projections.Rebuild.class)
    static final class Projections implements Runnable {
        @ParentCommand MiniPaintDexCli root;
        public void run() { CommandLine.usage(this, System.out); }
        @Command(name = "rebuild")
        static final class Rebuild implements Callable<Integer> {
            @ParentCommand Projections parent;
            public Integer call() { parent.root.output(Map.of("projection", parent.root.service().rebuildProjections())); return 0; }
        }
    }

    @Command(name = "exports", subcommands = Exports.Paints.class)
    static final class Exports implements Runnable {
        @ParentCommand MiniPaintDexCli root;
        public void run() { CommandLine.usage(this, System.out); }
        @Command(name = "paints")
        static final class Paints implements Callable<Integer> {
            @ParentCommand Exports parent;
            @Option(names = "--type", required = true) String type;
            public Integer call() { System.out.print(parent.root.service().exportPaints(type)); return 0; }
        }
    }
}
