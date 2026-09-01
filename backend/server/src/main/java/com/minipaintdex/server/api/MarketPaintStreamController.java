package com.minipaintdex.server.api;

import com.minipaintdex.application.usecase.MarketCatalogUseCases;
import com.minipaintdex.application.query.SearchMarketPaintsQuery;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/market/paints")
final class MarketPaintStreamController {
    private final MarketCatalogUseCases service;
    private final JsonMapper json = JsonMapper.builder().build();

    MarketPaintStreamController(MarketCatalogUseCases service) {
        this.service = service;
    }

    @GetMapping(value = "/stream", produces = "application/x-ndjson")
    StreamingResponseBody stream(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String range,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String applicationMethod,
            @RequestParam(required = false) String applicationSystem,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) String finish,
            @RequestParam(required = false) String medium,
            @RequestParam(required = false) String coverage,
            @RequestParam(required = false) String effect,
            @RequestParam(required = false) String undercoat,
            @RequestParam(required = false) String lifecycle) {
        var filters = new SearchMarketPaintsQuery(
                query, brand, range, role, applicationMethod, applicationSystem,
                color, finish, medium, coverage, effect, undercoat, lifecycle);
        return output -> {
            try (var paints = service.streamMarketPaints(filters)) {
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
