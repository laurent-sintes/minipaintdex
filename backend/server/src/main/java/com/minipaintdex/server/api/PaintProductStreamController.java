package com.minipaintdex.server.api;

import java.util.List;

import com.minipaintdex.application.usecase.MarketCatalogUseCases;
import com.minipaintdex.application.query.SearchPaintProductsQuery;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/market/paint-products")
final class PaintProductStreamController {
    private final MarketCatalogUseCases service;
    private final JsonMapper json = JsonMapper.builder().build();

    PaintProductStreamController(MarketCatalogUseCases service) {
        this.service = service;
    }

    @GetMapping(value = "/stream", produces = "application/x-ndjson")
    StreamingResponseBody stream(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) List<String> brand,
            @io.swagger.v3.oas.annotations.Parameter(description = "Repeatable brand::range selection; OR with brands. Escape literal colons and backslashes with a backslash.") @RequestParam(required = false) List<String> range,
            @RequestParam(required = false) List<String> role,
            @RequestParam(required = false) List<String> applicationMethod,
            @RequestParam(required = false) List<String> applicationSystem,
            @RequestParam(required = false) List<String> color,
            @RequestParam(required = false) List<String> finish,
            @RequestParam(required = false) List<String> medium,
            @RequestParam(required = false) List<String> coverage,
            @RequestParam(required = false) List<String> effect,
            @RequestParam(required = false) List<String> undercoat,
            @RequestParam(required = false) List<String> lifecycle) {
        var filters = SearchPaintProductsQuery.fromSelections(
                query, brand, range, role, applicationMethod, applicationSystem,
                color, finish, medium, coverage, effect, undercoat, lifecycle);
        return output -> {
            try (var paints = service.streamPaintProducts(filters)) {
                var iterator = paints.iterator();
                while (iterator.hasNext()) {
                    output.write(json.writeValueAsString(iterator.next()).getBytes(StandardCharsets.UTF_8));
                    output.write('\n');
                    output.flush();
                }
            }
        };
    }
}
