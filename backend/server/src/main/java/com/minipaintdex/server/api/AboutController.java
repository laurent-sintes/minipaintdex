package com.minipaintdex.server.api;

import com.minipaintdex.bootstrap.MiniPaintDexProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

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
    AboutResponse about() {
        return new AboutResponse(
                properties.application().name(),
                buildProperties == null ? "development" : buildProperties.getVersion(),
                properties.application().author());
    }

    @GetMapping("/documentation")
    DocumentationResponse documentation(@RequestParam(required = false) String audience) {
        var documents = DOCUMENTS.stream()
                .filter(document -> audience == null || audience.isBlank() || audience.equals(document.audience()))
                .map(document -> new DocumentationEntry(
                        document.id(), document.audience(), read(document.classpath())))
                .toList();
        return new DocumentationResponse(documents);
    }

    private static String read(String classpath) {
        try (var stream = new ClassPathResource(classpath).getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Documentation resource is unavailable: " + classpath, failure);
        }
    }

    private record DocumentResource(String id, String audience, String classpath) {}
    record AboutResponse(String name, String version, String author) {}
    record DocumentationEntry(String id, String audience, String markdown) {}
    record DocumentationResponse(List<DocumentationEntry> documents) {}
}
