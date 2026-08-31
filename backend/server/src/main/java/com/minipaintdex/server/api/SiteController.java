package com.minipaintdex.server.api;

import com.minipaintdex.application.usecase.SiteQueries;
import com.minipaintdex.application.view.DashboardView;
import com.minipaintdex.application.view.SiteConfigurationView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
final class SiteController {
    private final SiteQueries queries;

    SiteController(SiteQueries queries) {
        this.queries = queries;
    }

    @GetMapping("/site/config")
    Map<String, Object> siteConfig() {
        return section(queries.siteConfiguration().root());
    }

    @GetMapping("/dashboard")
    DashboardView dashboard() {
        return queries.dashboard();
    }

    private static Map<String, Object> section(SiteConfigurationView.Section section) {
        var result = new LinkedHashMap<String, Object>();
        section.entries().forEach(entry -> result.put(entry.key(), value(entry.value())));
        return java.util.Collections.unmodifiableMap(result);
    }

    private static Object value(SiteConfigurationView.Value value) {
        return switch (value) {
            case SiteConfigurationView.TextValue text -> text.value();
            case SiteConfigurationView.NumberValue number -> number.value();
            case SiteConfigurationView.BooleanValue bool -> bool.value();
            case SiteConfigurationView.ListValue list -> list.values().stream().map(SiteController::value).toList();
            case SiteConfigurationView.Section nested -> section(nested);
        };
    }
}
