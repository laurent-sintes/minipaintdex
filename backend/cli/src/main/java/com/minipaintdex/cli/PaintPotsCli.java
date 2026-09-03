package com.minipaintdex.cli;

import com.minipaintdex.application.command.*;
import com.minipaintdex.application.query.PageQuery;
import com.minipaintdex.application.query.SearchPaintPotsQuery;
import picocli.CommandLine;
import picocli.CommandLine.*;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

@Command(name = "paint-pots", mixinStandardHelpOptions = true, subcommands = {
        PaintPotsCli.Search.class, PaintPotsCli.Show.class, PaintPotsCli.Add.class, PaintPotsCli.Import.class,
        PaintPotsCli.Observe.class, PaintPotsCli.Open.class, PaintPotsCli.Possession.class, PaintPotsCli.Note.class, PaintPotsCli.Photo.class,
        PaintPotsCli.PhotoPreview.class})
final class PaintPotsCli implements Runnable {
    @ParentCommand MiniPaintDexCli.Workshop parent;
    MiniPaintDexCli root() { return parent.root; }
    public void run() { CommandLine.usage(this, System.out); }

    @Command(name = "search", mixinStandardHelpOptions = true)
    static final class Search implements Callable<Integer> {
        @ParentCommand PaintPotsCli parent;
        @Option(names = "--paint-product-id") String product;
        @Option(names = "--include-removed") boolean includeRemoved;
        @Option(names = "--page", defaultValue = "0") int page;
        @Option(names = "--size", defaultValue = "50") int size;
        public Integer call() {
            var result = parent.root().workshop().searchPaintPots(new SearchPaintPotsQuery(product, includeRemoved, new PageQuery(page, size, List.of())));
            parent.root().output(Map.of("pots", result.content(), "total", result.totalElements(), "page", result.page(), "size", result.size(), "totalPages", result.totalPages()));
            return 0;
        }
    }
    @Command(name = "show", mixinStandardHelpOptions = true)
    static final class Show implements Callable<Integer> {
        @ParentCommand PaintPotsCli parent;
        @Option(names = "--paint-pot-id", required = true) String id;
        public Integer call() { parent.root().output(parent.root().workshop().getPaintPot(id)); return 0; }
    }
    @Command(name = "add", mixinStandardHelpOptions = true)
    static final class Add implements Callable<Integer> {
        @ParentCommand PaintPotsCli parent;
        @Option(names = "--paint-pot-id", required = true) String id;
        @Option(names = "--paint-product-id", required = true) String product;
        @Option(names = "--acquired-at") Instant acquiredAt;
        @Option(names = "--actor") String actor;
        @Option(names = "--correlation-id") String correlation;
        @Option(names = "--idempotency-key") String key;
        public Integer call() throws Exception {
            var root = parent.root();
            var command = new RegisterPaintPotCommand(id, product, acquiredAt, actor, correlation, key);
            root.output(root.mutateJson("/api/v1/workshop/paint-pots",
                    MiniPaintDexCli.body("paintPotId", id, "paintProductId", product, "acquiredAt", acquiredAt, "actorId", actor),
                    key, correlation, () -> Map.of("result", root.workshop().registerPaintPot(command))));
            return 0;
        }
    }
    @Command(name = "import", mixinStandardHelpOptions = true, description = "Merge stable pot registrations; dry-run unless --apply")
    static final class Import implements Callable<Integer> {
        @ParentCommand PaintPotsCli parent;
        @Option(names = "--input", required = true) Path input;
        @Option(names = "--apply") boolean apply;
        @Option(names = "--actor") String actor;
        @Option(names = "--correlation-id") String correlation;
        @Option(names = "--idempotency-key") String key;
        public Integer call() throws Exception {
            var payload = JsonMapper.builder().build().readValue(Files.readString(input), new TypeReference<Map<String, Object>>() {});
            parent.root().output(importPayload(parent.root(), payload, !apply, actor, correlation, key));
            return 0;
        }
    }
    static Object importPayload(MiniPaintDexCli root, Map<String, Object> payload, boolean dryRun, String actor, String correlation, String key) throws Exception {
        var json = JsonMapper.builder().build();
        var input = json.convertValue(payload, PotImport.class);
        var command = new ImportPaintPotsCommand(input.schemaVersion(), input.kind(), input.pots(), dryRun, actor, correlation, key);
        return root.mutateJson("/api/v1/workshop/paint-pot-imports?dryRun=" + dryRun, withActor(payload, actor), key, correlation,
                () -> Map.of("result", root.workshop().importPaintPots(command)));
    }
    private static Map<String, Object> withActor(Map<String, Object> payload, String actor) {
        var result = new java.util.LinkedHashMap<>(payload);
        if (actor != null) result.put("actorId", actor);
        return result;
    }
    record PotImport(int schemaVersion, String kind, List<ImportPaintPotsCommand.Registration> pots) {}
    abstract static class Mutation {
        @ParentCommand PaintPotsCli parent;
        @Option(names = "--paint-pot-id", required = true) String id;
        @Option(names = "--actor") String actor;
        @Option(names = "--occurred-at") Instant at;
        @Option(names = "--correlation-id") String correlation;
        @Option(names = "--idempotency-key") String key;
        int send(String action, Map<String, Object> body, Supplier<Object> offline) throws Exception {
            body.putAll(MiniPaintDexCli.body("actorId", actor, "occurredAt", at));
            parent.root().output(parent.root().mutateJson("/api/v1/workshop/paint-pots/" + id + "/" + action, body, key, correlation, offline));
            return 0;
        }
    }
    @Command(name = "observe", mixinStandardHelpOptions = true)
    static final class Observe extends Mutation implements Callable<Integer> {
        @Option(names = "--condition", required = true) String condition;
        @Option(names = "--remaining-level", required = true) String remaining;
        public Integer call() throws Exception {
            return send("observations", MiniPaintDexCli.body("condition", condition, "remainingLevel", remaining),
                    () -> Map.of("publication", parent.root().workshop().observePaintPot(new ObservePaintPotCommand(id, condition, remaining, actor, at, correlation, key))));
        }
    }
    @Command(name = "open", mixinStandardHelpOptions = true)
    static final class Open extends Mutation implements Callable<Integer> {
        public Integer call() throws Exception {
            return send("openings", MiniPaintDexCli.body(), () -> Map.of("publication",
                    parent.root().workshop().openPaintPot(new OpenPaintPotCommand(id, actor, at, correlation, key))));
        }
    }
    @Command(name = "set-possession", mixinStandardHelpOptions = true)
    static final class Possession extends Mutation implements Callable<Integer> {
        @Option(names = "--possession", required = true) String possession;
        public Integer call() throws Exception {
            return send("possession-changes", MiniPaintDexCli.body("possession", possession), () -> Map.of("publication",
                    parent.root().workshop().changePaintPotPossession(new ChangePaintPotPossessionCommand(id, possession, actor, at, correlation, key))));
        }
    }
    @Command(name = "note", mixinStandardHelpOptions = true)
    static final class Note extends Mutation implements Callable<Integer> {
        @Option(names = "--text", required = true) String text;
        public Integer call() throws Exception {
            return send("notes", MiniPaintDexCli.body("note", text), () -> Map.of("publication",
                    parent.root().workshop().addPaintPotNote(new AddPaintPotNoteCommand(id, text, actor, at, correlation, key))));
        }
    }
    @Command(name = "photo", mixinStandardHelpOptions = true)
    static final class Photo extends Mutation implements Callable<Integer> {
        @Option(names = "--file", required = true) Path file;
        @Option(names = "--caption") String caption;
        @Option(names = "--remove-background") boolean removeBackground;
        public Integer call() throws Exception {
            var type = Files.probeContentType(file);
            if (type == null) type = file.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".webp") ? "image/webp" : "image/jpeg";
            var command = new AddPaintPotPhotoCommand(id, file.getFileName().toString(), type, Files.readAllBytes(file), caption, actor, at, correlation, key, removeBackground);
            parent.root().output(parent.root().mutatePhoto("/api/v1/workshop/paint-pots/" + id + "/photos",
                    file, type, null, caption, actor, at, key, correlation, removeBackground,
                    () -> Map.of("publication", parent.root().workshop().addPaintPotPhoto(command))));
            return 0;
        }
    }

    @Command(name = "photo-preview", mixinStandardHelpOptions = true, description = "Write a transient transparent PNG without changing workshop data")
    static final class PhotoPreview implements Callable<Integer> {
        @ParentCommand PaintPotsCli parent;
        @Option(names = "--paint-pot-id", required = true) String id;
        @Option(names = "--file", required = true) Path file;
        @Option(names = "--output", required = true) Path output;
        @Option(names = "--correlation-id") String correlation;
        public Integer call() throws Exception {
            if (Files.exists(output)) throw new com.minipaintdex.domain.shared.DomainException("conflict", "Preview output already exists.");
            var type = Files.probeContentType(file);
            if (type == null) type = file.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".webp") ? "image/webp" : "image/jpeg";
            var preview = parent.root().workshop().previewPaintPotPhoto(
                    new com.minipaintdex.application.query.PreviewPaintPotPhotoQuery(id, type, Files.readAllBytes(file), correlation));
            Files.write(output, preview.content(), java.nio.file.StandardOpenOption.CREATE_NEW);
            parent.root().output(Map.of("output", output.toAbsolutePath().toString(), "contentType", "image/png",
                    "processingMethod", preview.processingMethod(), "correlationId", preview.correlationId(), "size", preview.content().length));
            return 0;
        }
    }
}
