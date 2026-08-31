package com.minipaintdex.server;

import com.minipaintdex.bootstrap.MiniPaintDexProperties;
import com.minipaintdex.application.port.PersistenceLifecycle;
import com.minipaintdex.domain.market.paint.PaintMatchEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

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
                "minipaintdex.paint-matching.standard.functional-type=0.25"
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

    @Test
    void bindsTypedSpringPropertiesIntoInfrastructureAndDomainPolicies() {
        assertEquals(0.55, paintMatchEngine.policy().standard().color());
        assertEquals(0.25, paintMatchEngine.policy().standard().functionalType());
        assertEquals(5, properties.paintMatching().candidateLimit());
        assertTrue(properties.storage().marketPaintCatalog().endsWith("data/market/paints/catalog.yaml"));
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
}
