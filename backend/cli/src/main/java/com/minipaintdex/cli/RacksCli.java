package com.minipaintdex.cli;

import com.minipaintdex.application.query.*;
import com.minipaintdex.application.command.SaveRackReferenceCommand;
import com.minipaintdex.application.storage.StorageContracts.*;
import picocli.CommandLine;
import picocli.CommandLine.*;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Callable;

final class RacksCli {
    private RacksCli() {}
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static Map<String,Object> payload(Path input) throws Exception { return JSON.readValue(Files.readString(input), new TypeReference<Map<String,Object>>() {}); }
    private static <T> T command(Map<String,Object> payload, Class<T> type, String correlation, String key) {
        var values = new LinkedHashMap<>(payload); values.put("correlationId", correlation);
        if (key != null) values.put("idempotencyKey", key);
        return JSON.convertValue(values, type);
    }
    abstract static class Paging {
        @Option(names="--page", defaultValue="0") int page;
        @Option(names="--size", defaultValue="24") int size;
        @Option(names="--query") String query;
        @Option(names="--correlation-id") String correlation = UUID.randomUUID().toString();
        PageQuery paging() { return new PageQuery(page, size, List.of()); }
    }
    abstract static class Input {
        @Option(names="--input", required=true) Path input;
        @Option(names="--idempotency-key") String key = UUID.randomUUID().toString();
        @Option(names="--correlation-id") String correlation = UUID.randomUUID().toString();
    }

    @Command(name="rack-products", mixinStandardHelpOptions=true, subcommands={RackProducts.Search.class, RackProducts.Show.class, RackProducts.Save.class})
    static final class RackProducts implements Runnable {
        @ParentCommand MiniPaintDexCli.Market parent;
        public void run() { CommandLine.usage(this, System.out); }
        @Command(name="search", mixinStandardHelpOptions=true)
        static final class Search extends Paging implements Callable<Integer> {
            @ParentCommand RackProducts parent;
            public Integer call() { parent.parent.root.output(parent.parent.root.market().searchRackProducts(new RackCatalogQuery(paging(), query, correlation))); return 0; }
        }
        @Command(name="show", mixinStandardHelpOptions=true)
        static final class Show implements Callable<Integer> {
            @ParentCommand RackProducts parent;
            @Option(names="--id", required=true) String id;
            @Option(names="--correlation-id") String correlation = UUID.randomUUID().toString();
            public Integer call() { parent.parent.root.output(parent.parent.root.market().getRackProduct(new GetRackReferenceQuery(id, correlation))); return 0; }
        }
        @Command(name="save", mixinStandardHelpOptions=true)
        static final class Save extends Input implements Callable<Integer> {
            @ParentCommand RackProducts parent;
            public Integer call() throws Exception {
                var root=parent.parent.root; var data=payload(input);
                var cmd=command(data, SaveRackReferenceCommand.class, correlation, null);
                root.output(root.mutateJson("/api/v1/market/rack-catalog/entries", data, null, correlation,
                        () -> Map.of("catalogRevision", root.administration().saveRackReference(cmd), "correlationId", correlation))); return 0;
            }
        }
    }

    @Command(name="container-formats", mixinStandardHelpOptions=true, subcommands={ContainerFormats.Search.class, ContainerFormats.Show.class, ContainerFormats.Save.class})
    static final class ContainerFormats implements Runnable {
        @ParentCommand MiniPaintDexCli.Market parent;
        public void run() { CommandLine.usage(this, System.out); }
        @Command(name="search", mixinStandardHelpOptions=true)
        static final class Search extends Paging implements Callable<Integer> {
            @ParentCommand ContainerFormats parent;
            public Integer call() { parent.parent.root.output(parent.parent.root.market().searchContainerFormats(new RackCatalogQuery(paging(), query, correlation))); return 0; }
        }
        @Command(name="show", mixinStandardHelpOptions=true)
        static final class Show implements Callable<Integer> {
            @ParentCommand ContainerFormats parent;
            @Option(names="--id", required=true) String id;
            @Option(names="--correlation-id") String correlation = UUID.randomUUID().toString();
            public Integer call() { parent.parent.root.output(parent.parent.root.market().getContainerFormat(new GetRackReferenceQuery(id, correlation))); return 0; }
        }
        @Command(name="save", mixinStandardHelpOptions=true)
        static final class Save extends Input implements Callable<Integer> {
            @ParentCommand ContainerFormats parent;
            public Integer call() throws Exception {
                var root=parent.parent.root; var data=payload(input);
                var cmd=command(data, SaveRackReferenceCommand.class, correlation, null);
                root.output(root.mutateJson("/api/v1/market/rack-catalog/entries", data, null, correlation,
                        () -> Map.of("catalogRevision", root.administration().saveRackReference(cmd), "correlationId", correlation))); return 0;
            }
        }
    }

    @Command(name="racks", mixinStandardHelpOptions=true, subcommands={WorkshopRacks.ListRacksCommand.class, WorkshopRacks.Show.class, WorkshopRacks.Save.class, WorkshopRacks.Add.class})
    static final class WorkshopRacks implements Runnable {
        @ParentCommand MiniPaintDexCli.Workshop parent;
        public void run() { CommandLine.usage(this, System.out); }
        @Command(name="add", mixinStandardHelpOptions=true)
        static final class Add extends Input implements Callable<Integer> {
            @ParentCommand WorkshopRacks parent;
            public Integer call() throws Exception {
                var root=parent.parent.root; var data=payload(input); var cmd=command(data, AddRacks.class, correlation, key);
                root.output(root.mutateJson("/api/v1/workshop/rack-acquisitions", data, key, correlation,
                        () -> Map.of("publication", root.workshop().addWorkshopRacks(cmd)))); return 0;
            }
        }
        @Command(name="list", mixinStandardHelpOptions=true)
        static final class ListRacksCommand extends Paging implements Callable<Integer> {
            @ParentCommand WorkshopRacks parent;
            public Integer call() { parent.parent.root.output(parent.parent.root.workshop().listWorkshopRacks(new ListRacks(paging(), correlation))); return 0; }
        }
        @Command(name="show", mixinStandardHelpOptions=true)
        static final class Show implements Callable<Integer> {
            @ParentCommand WorkshopRacks parent;
            @Option(names="--workshop-rack-id", required=true) String id;
            @Option(names="--correlation-id") String correlation=UUID.randomUUID().toString();
            public Integer call() { parent.parent.root.output(parent.parent.root.workshop().getWorkshopRack(new GetRack(id, correlation))); return 0; }
        }
        @Command(name="save", mixinStandardHelpOptions=true)
        static final class Save extends Input implements Callable<Integer> {
            @ParentCommand WorkshopRacks parent;
            public Integer call() throws Exception {
                var root=parent.parent.root; var data=payload(input); var cmd=command(data, SaveRack.class, correlation, key);
                root.output(root.mutateJson("/api/v1/workshop/racks", data, key, correlation,
                        () -> Map.of("publication", root.workshop().saveWorkshopRack(cmd)))); return 0;
            }
        }
    }
    @Command(name="paint-storage", mixinStandardHelpOptions=true, subcommands={PaintStorage.Pots.class, PaintStorage.PreviewCommand.class,
            PaintStorage.ConfirmCommand.class, PaintStorage.IdentifyContainerCommand.class, PaintStorage.SetPlacementCommand.class})
    static final class PaintStorage implements Runnable {
        @ParentCommand MiniPaintDexCli.Workshop parent;
        public void run() { CommandLine.usage(this, System.out); }
        @Command(name="pots", mixinStandardHelpOptions=true)
        static final class Pots extends Paging implements Callable<Integer> {
            @ParentCommand PaintStorage parent;
            @Option(names="--unplaced-only") boolean unplaced;
            public Integer call() { parent.parent.root.output(parent.parent.root.workshop().searchStoragePots(new SearchPots(paging(), query, unplaced, correlation))); return 0; }
        }
        @Command(name="preview", mixinStandardHelpOptions=true)
        static final class PreviewCommand extends Input implements Callable<Integer> {
            @ParentCommand PaintStorage parent;
            public Integer call() throws Exception {
                parent.parent.root.output(parent.parent.root.workshop().previewPaintStorage(command(payload(input), Preview.class, correlation, null))); return 0;
            }
        }

        @Command(name="confirm", mixinStandardHelpOptions=true)
        static final class ConfirmCommand extends Input implements Callable<Integer> {
            @ParentCommand PaintStorage parent;
            public Integer call() throws Exception {
                var root=parent.parent.root; var data=payload(input); var cmd=command(data, Confirm.class, correlation, key);
                root.output(root.mutateJson("/api/v1/workshop/paint-storage/confirmations", data, key, correlation,
                        () -> Map.of("publication", root.workshop().confirmPaintStorage(cmd)))); return 0;
            }
        }

        @Command(name="identify-container", mixinStandardHelpOptions=true)
        static final class IdentifyContainerCommand extends Input implements Callable<Integer> {
            @ParentCommand PaintStorage parent;
            public Integer call() throws Exception {
                var root=parent.parent.root; var data=payload(input); var cmd=command(data, IdentifyContainer.class, correlation, key);
                root.output(root.mutateJson("/api/v1/workshop/paint-storage/container-identifications", data, key, correlation,
                        () -> Map.of("publication", root.workshop().identifyPaintPotContainer(cmd)))); return 0;
            }
        }

        @Command(name="set-placement", mixinStandardHelpOptions=true)
        static final class SetPlacementCommand extends Input implements Callable<Integer> {
            @ParentCommand PaintStorage parent;
            public Integer call() throws Exception {
                var root=parent.parent.root; var data=payload(input); var cmd=command(data, SetPlacement.class, correlation, key);
                root.output(root.mutateJson("/api/v1/workshop/paint-storage/placements", data, key, correlation,
                        () -> Map.of("publication", root.workshop().setPaintPotPlacement(cmd)))); return 0;
            }
        }
    }
}
