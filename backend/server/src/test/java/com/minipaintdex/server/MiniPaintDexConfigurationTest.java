package com.minipaintdex.server;

import com.minipaintdex.bootstrap.MiniPaintDexProperties;
import com.minipaintdex.application.port.PersistenceLifecycle;
import com.minipaintdex.domain.market.paint.PaintMatchEngine;
import com.minipaintdex.application.usecase.MarketCatalogUseCases;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import jakarta.validation.Validator;

import java.nio.file.Path;
import java.time.Duration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = MiniPaintDexServer.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "minipaintdex.root=${user.dir}/../..",
                "minipaintdex.paint-matching.standard.color=0.55",
                "minipaintdex.paint-matching.standard.role=0.25"
        })
@AutoConfigureMockMvc
class MiniPaintDexConfigurationTest {
    @Autowired
    MiniPaintDexProperties properties;

    @Autowired
    PaintMatchEngine paintMatchEngine;

    @Autowired
    PersistenceLifecycle persistence;

    @Autowired
    MockMvc mvc;

    @Autowired
    Validator validator;

    @Autowired
    MarketCatalogUseCases market;

    @Test
    void bindsTypedSpringPropertiesIntoInfrastructureAndDomainPolicies() {
        assertEquals(0.55, paintMatchEngine.policy().standard().color());
        assertEquals(0.25, paintMatchEngine.policy().standard().role());
        assertEquals(5, properties.paintMatching().candidateLimit());
        assertTrue(properties.storage().marketPaintCatalogDirectory().endsWith("data/market/paints"));
        assertEquals("files", persistence.status().storage());
        assertEquals(1, properties.eventing().workerCount());
    }

    @Test
    void registersSpringPagingAndPublishesTheGeneratedOpenApiContract() throws Exception {
        mvc.perform(get("/api/v1/market/paints").queryParam("page", "0").queryParam("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paints.length()").value(2))
                .andExpect(jsonPath("$.paints[0].quantity").doesNotExist())
                .andExpect(jsonPath("$._links.self.href").exists());
        mvc.perform(get("/api/v1/workshop/paints").queryParam("page", "0").queryParam("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paints.length()").value(2))
                .andExpect(jsonPath("$.paints[0].marketPaint.id").exists())
                .andExpect(jsonPath("$.paints[0].quantity").isNumber())
                .andExpect(jsonPath("$._links.market-catalog.href").value("/api/v1/market/paints"));
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.paths['/api/v1/workshop/painting-project-import-previews/{productId}']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/workshop/paints']").exists());
    }

    @Test
    void rejectsNonPositiveLifecycleDurations() {
        var storage = new MiniPaintDexProperties.Storage(
                Path.of("site"), Path.of("paints"), Path.of("inventory"), Path.of("shopping"),
                Path.of("products"), Path.of("guides"), Path.of("ledger"), Path.of("publications"),
                Path.of("media"), true, Duration.ZERO);
        var eventing = new MiniPaintDexProperties.Eventing(
                1, 1, 1, Duration.ZERO, Duration.ZERO);

        assertTrue(validator.validate(storage).stream().anyMatch(violation ->
                violation.getPropertyPath().toString().equals("sentinelIntervalPositive")));
        assertTrue(validator.validate(eventing).stream().anyMatch(violation ->
                violation.getPropertyPath().toString().equals("shutdownTimeoutPositive")));
    }

    @Test
    void validatesTheCanonicalDevelopmentDatasetThroughTheApplicationBoundary() {
        var product = market.getMarketPaintableProduct("reichbusters-reloaded");

        assertEquals(198, product.expectedPaintableCount());
        assertEquals(198, product.items().stream().mapToInt(item -> item.quantity()).sum());
        assertTrue(market.marketPaintFacets(com.minipaintdex.application.query.SearchMarketPaintsQuery.empty())
                .total() > 1_500);
    }
}
