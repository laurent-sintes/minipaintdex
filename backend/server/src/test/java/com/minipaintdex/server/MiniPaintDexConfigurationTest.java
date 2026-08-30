package com.minipaintdex.server;

import com.minipaintdex.adapter.file.FileRepositoryLayout;
import com.minipaintdex.bootstrap.MiniPaintDexProperties;
import com.minipaintdex.domain.paint.PaintMatchEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = MiniPaintDexServer.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "minipaintdex.paint-matching.standard.color=0.55",
                "minipaintdex.paint-matching.standard.functional-type=0.25"
        })
class MiniPaintDexConfigurationTest {
    @Autowired
    MiniPaintDexProperties properties;

    @Autowired
    PaintMatchEngine paintMatchEngine;

    @Autowired
    FileRepositoryLayout layout;

    @Test
    void bindsTypedSpringPropertiesIntoInfrastructureAndDomainPolicies() {
        assertEquals(0.55, paintMatchEngine.policy().standard().color());
        assertEquals(0.25, paintMatchEngine.policy().standard().functionalType());
        assertEquals(5, properties.paintMatching().candidateLimit());
        assertTrue(layout.marketPaintCatalog().endsWith("data/market/paints/catalog.yaml"));
        assertTrue(layout.mediaDirectory().isAbsolute());
    }
}
