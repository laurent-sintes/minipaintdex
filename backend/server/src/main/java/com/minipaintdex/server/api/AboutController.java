package com.minipaintdex.server.api;

import com.minipaintdex.bootstrap.MiniPaintDexProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
class AboutController {
    private static final List<DocumentResource> DOCUMENTS = List.of(
            new DocumentResource("user-guide", "user", "documentation/user/README.md"),
            new DocumentResource("ddd-model", "administrator", "documentation/admin/ddd.md"),
            new DocumentResource("rest-api", "administrator", "documentation/admin/rest-api.md"),
            new DocumentResource("skills", "administrator", "documentation/admin/skills.md"));

    private final MiniPaintDexProperties properties;
    private final BuildProperties buildProperties;

    AboutController(
            MiniPaintDexProperties properties,
            ObjectProvider<BuildProperties> buildProperties) {
        this.properties = properties;
        this.buildProperties = buildProperties.getIfAvailable();
    }

    @GetMapping("/about")
    Map<String, Object> about() {
        return Map.of(
                "name", properties.application().name(),
                "version", buildProperties == null ? "development" : buildProperties.getVersion(),
                "author", properties.application().author());
    }

    @GetMapping("/documentation")
    Map<String, Object> documentation() {
        var documents = DOCUMENTS.stream().map(document -> Map.of(
                "id", document.id(),
                "audience", document.audience(),
                "markdown", read(document.classpath()))).toList();
        return Map.of("documents", documents);
    }

    private static String read(String classpath) {
        try (var stream = new ClassPathResource(classpath).getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Documentation resource is unavailable: " + classpath, failure);
        }
    }

    private record DocumentResource(String id, String audience, String classpath) {}
}
