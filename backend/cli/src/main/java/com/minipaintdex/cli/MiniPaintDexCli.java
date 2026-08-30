package com.minipaintdex.cli;

import com.minipaintdex.adapter.file.FileMiniPaintDexRepository;
import com.minipaintdex.application.MiniPaintDexService;
import com.minipaintdex.application.command.AddWorkshopItemCommand;
import com.minipaintdex.application.command.ApplyMarketPaintChangeSetCommand;
import com.minipaintdex.application.command.ApplyMiniatureProjectChangeSetCommand;
import com.minipaintdex.application.command.AssignWorkshopRecipeCommand;
import com.minipaintdex.application.command.CreateWorkshopRecipeCommand;
import com.minipaintdex.application.command.TransitionStageCommand;
import com.minipaintdex.application.command.TransitionWorkshopRecipeCommand;
import com.minipaintdex.application.query.SearchMarketPaintsQuery;
import com.minipaintdex.domain.workflow.DomainException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
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
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
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
            service = new MiniPaintDexService(repository, repository, repository, repository, repository);
        }
        return service;
    }

    void output(Object value) {
        System.out.println(json.writeValueAsString(value));
    }

    void error(String code, String message) {
        System.err.println(json.writeValueAsString(Map.of("error", Map.of("code", code, "message", message))));
    }

    ApplyMarketPaintChangeSetCommand readPaintChangeSet(Path path, boolean dryRun) throws Exception {
        var payload = json.readValue(Files.readString(path), MAP_TYPE);
        var rawOperations = payload.get("operations") instanceof List<?> list ? list : List.of();
        var operations = rawOperations.stream()
                .filter(Map.class::isInstance)
                .map(value -> (Map<?, ?>) value)
                .map(value -> new ApplyMarketPaintChangeSetCommand.Operation(
                        String.valueOf(value.get("action")), stringMap(value.get("record")),
                        value.get("workshop_quantity_delta") == null ? 0 : number(value.get("workshop_quantity_delta")),
                        Boolean.TRUE.equals(value.get("confirmed_removal"))))
                .toList();
        return new ApplyMarketPaintChangeSetCommand(
                number(payload.get("schema_version")), String.valueOf(payload.get("kind")), operations, dryRun);
    }

    ApplyMiniatureProjectChangeSetCommand readProjectChangeSet(Path path, boolean dryRun) throws Exception {
        var payload = json.readValue(Files.readString(path), MAP_TYPE);
        var items = mapList(payload.get("workshop_items")).stream()
                .map(item -> new ApplyMiniatureProjectChangeSetCommand.WorkshopItem(
                        String.valueOf(item.get("id")), String.valueOf(item.get("catalog_item_id")),
                        String.valueOf(item.get("project_id")), String.valueOf(item.get("display_name"))))
                .toList();
        return new ApplyMiniatureProjectChangeSetCommand(
                number(payload.get("schema_version")), String.valueOf(payload.get("kind")),
                stringMap(payload.get("project")), mapList(payload.get("painting_guides")), items, dryRun,
                nullable(payload.get("actor_id")), nullable(payload.get("correlation_id")));
    }

    CreateWorkshopRecipeCommand readWorkshopRecipe(Path path, String correlationId, String idempotencyKey) throws Exception {
        var payload = json.readValue(Files.readString(path), MAP_TYPE);
        return new CreateWorkshopRecipeCommand(
                nullable(payload.get("recipe_id")), String.valueOf(payload.get("catalog_item_id")),
                nullable(payload.get("based_on_guide_id")), nullable(payload.get("supersedes_recipe_id")),
                String.valueOf(payload.get("display_name")), number(payload.get("version")),
                mapList(payload.get("solutions")), nullable(payload.get("actor_id")),
                payload.get("occurred_at") == null ? null : Instant.parse(String.valueOf(payload.get("occurred_at"))),
                correlationId, idempotencyKey);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> stringMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Map.class::isInstance).map(MiniPaintDexCli::stringMap).toList();
    }

    private static String nullable(Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
    }

    private static int number(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
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

    @Command(name = "market", subcommands = {Market.Paints.class, Market.Games.class, Market.Guides.class})
    static final class Market implements Runnable {
        @ParentCommand MiniPaintDexCli root;
        public void run() { CommandLine.usage(this, System.out); }

        @Command(name = "paints", subcommands = {Paints.Search.class, Paints.Apply.class})
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
                @Option(names = "--finish") String finish;
                @Option(names = "--medium") String medium;
                @Option(names = "--opacity") String opacity;
                @Option(names = "--volume") String volume;
                @Option(names = "--reference") String reference;
                @Option(names = "--lifecycle") String lifecycle;
                @Option(names = "--manufacturer") String manufacturer;
                @Option(names = "--tag") String tag;
                public Integer call() {
                    var root = parent.parent.root;
                    root.output(Map.of("paints", root.service().searchMarketPaints(new SearchMarketPaintsQuery(
                            query, brand, range, type, color, finish, medium, opacity, volume,
                            reference, lifecycle, manufacturer, tag))));
                    return 0;
                }
            }

            @Command(name = "apply", description = "Validate and apply a market-paint change set")
            static final class Apply implements Callable<Integer> {
                @ParentCommand Paints parent;
                @Option(names = "--input", required = true) Path input;
                @Option(names = "--dry-run") boolean dryRun;
                public Integer call() throws Exception {
                    var root = parent.parent.root;
                    root.output(Map.of("result", root.service().applyMarketPaintChangeSet(root.readPaintChangeSet(input, dryRun))));
                    return 0;
                }
            }
        }

        @Command(name = "games", subcommands = {Games.ListGames.class, Games.Apply.class})
        static final class Games implements Runnable {
            @ParentCommand Market parent;
            public void run() { CommandLine.usage(this, System.out); }
            @Command(name = "list")
            static final class ListGames implements Callable<Integer> {
                @ParentCommand Games parent;
                public Integer call() { var root = parent.parent.root; root.output(Map.of("games", root.service().listProjects())); return 0; }
            }
            @Command(name = "apply", description = "Validate and apply a miniature-project change set")
            static final class Apply implements Callable<Integer> {
                @ParentCommand Games parent;
                @Option(names = "--input", required = true) Path input;
                @Option(names = "--dry-run") boolean dryRun;
                public Integer call() throws Exception {
                    var root = parent.parent.root;
                    root.output(Map.of("result", root.service().applyMiniatureProjectChangeSet(root.readProjectChangeSet(input, dryRun))));
                    return 0;
                }
            }
        }

        @Command(name = "guides", subcommands = {Guides.ListGuides.class, Guides.Reconcile.class})
        static final class Guides implements Runnable {
            @ParentCommand Market parent;
            public void run() { CommandLine.usage(this, System.out); }
            @Command(name = "list", description = "List market painting guides")
            static final class ListGuides implements Callable<Integer> {
                @ParentCommand Guides parent;
                @Option(names = "--catalog-item") String catalogItem;
                public Integer call() {
                    var root = parent.parent.root;
                    root.output(Map.of("paintingGuides", root.service().listMarketPaintingGuides(catalogItem)));
                    return 0;
                }
            }
            @Command(name = "reconcile", description = "Rank owned-paint replacements for a market guide")
            static final class Reconcile implements Callable<Integer> {
                @ParentCommand Guides parent;
                @Option(names = "--guide", required = true) String guide;
                public Integer call() {
                    var root = parent.parent.root;
                    root.output(root.service().reconcileMarketPaintingGuide(guide));
                    return 0;
                }
            }
        }
    }

    @Command(name = "workshop", subcommands = {Workshop.Projects.class, Workshop.Items.class, Workshop.Stage.class, Workshop.Recipes.class})
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

        @Command(name = "recipes", subcommands = {Recipes.ListRecipes.class, Recipes.Create.class, Recipes.Transition.class, Recipes.Assign.class})
        static final class Recipes implements Runnable {
            @ParentCommand Workshop parent;
            public void run() { CommandLine.usage(this, System.out); }
            @Command(name = "list")
            static final class ListRecipes implements Callable<Integer> {
                @ParentCommand Recipes parent;
                @Option(names = "--catalog-item") String catalogItem;
                public Integer call() {
                    var root = parent.parent.root;
                    root.output(Map.of("recipes", root.service().listWorkshopRecipes(catalogItem)));
                    return 0;
                }
            }
            @Command(name = "create", description = "Create a draft workshop recipe from a JSON command")
            static final class Create implements Callable<Integer> {
                @ParentCommand Recipes parent;
                @Option(names = "--input", required = true) Path input;
                @Option(names = "--correlation-id") String correlationId;
                @Option(names = "--idempotency-key") String idempotencyKey;
                public Integer call() throws Exception {
                    var root = parent.parent.root;
                    root.output(Map.of("event", root.service().createWorkshopRecipe(root.readWorkshopRecipe(input, correlationId, idempotencyKey))));
                    return 0;
                }
            }
            @Command(name = "transition")
            static final class Transition implements Callable<Integer> {
                @ParentCommand Recipes parent;
                @Option(names = "--recipe", required = true) String recipe;
                @Option(names = "--action", required = true) String action;
                @Option(names = "--comment") String comment;
                @Option(names = "--actor") String actor;
                @Option(names = "--occurred-at") Instant occurredAt;
                @Option(names = "--correlation-id") String correlationId;
                @Option(names = "--idempotency-key") String idempotencyKey;
                public Integer call() {
                    var root = parent.parent.root;
                    root.output(Map.of("event", root.service().transitionWorkshopRecipe(new TransitionWorkshopRecipeCommand(
                            recipe, action, comment, actor, occurredAt, correlationId, idempotencyKey))));
                    return 0;
                }
            }
            @Command(name = "assign")
            static final class Assign implements Callable<Integer> {
                @ParentCommand Recipes parent;
                @Option(names = "--item", required = true) String item;
                @Option(names = "--recipe", required = true) String recipe;
                @Option(names = "--actor") String actor;
                @Option(names = "--occurred-at") Instant occurredAt;
                @Option(names = "--correlation-id") String correlationId;
                @Option(names = "--idempotency-key") String idempotencyKey;
                public Integer call() {
                    var root = parent.parent.root;
                    root.output(Map.of("event", root.service().assignWorkshopRecipe(new AssignWorkshopRecipeCommand(
                            item, recipe, actor, occurredAt, correlationId, idempotencyKey))));
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
