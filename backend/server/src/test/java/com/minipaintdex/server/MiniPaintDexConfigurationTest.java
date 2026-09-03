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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = MiniPaintDexServer.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "minipaintdex.root=${user.dir}/../..",
                "minipaintdex.paint-pot-photos.enabled=false",
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
        assertEquals(8, properties.paintSearch().defaultSuggestionLimit());
        assertTrue(properties.storage().paintProductCatalogDirectory().endsWith("data/market/paints"));
        assertEquals("files", persistence.status().storage());
        assertEquals(1, properties.eventing().workerCount());
    }

    @Test
    void publishesStockDetailWithHalLinksAndPhotoSelectionContract() throws Exception {
        var paint = market.searchPaintProducts(com.minipaintdex.application.query.SearchPaintProductsQuery.empty()).getFirst();
        mvc.perform(get("/api/v1/workshop/paint-stocks/" + paint.id()).header("X-Correlation-Id", "stock-read"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stock.paintProduct.id").value(paint.id()))
                .andExpect(jsonPath("$.stock.quantity").isNumber())
                .andExpect(jsonPath("$.stock.canReplacePhoto").isBoolean())
                .andExpect(jsonPath("$.correlationId").value("stock-read"))
                .andExpect(jsonPath("$._links.self.href").value("/api/v1/workshop/paint-stocks/" + paint.id()))
                .andExpect(jsonPath("$._links.paint-product.href").value("/api/v1/market/paint-products/" + paint.id()));
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/workshop/paint-stocks/{paintProductId}'].get.operationId").value("getWorkshopPaintStock"))
                .andExpect(jsonPath("$.components.schemas.WorkshopPaintStockView.properties.personalPhoto").exists())
                .andExpect(jsonPath("$.components.schemas.WorkshopPaintStockView.properties.canReplacePhoto").exists())
                .andExpect(jsonPath("$.components.schemas.WorkshopPaintStockView.properties.personalImage").doesNotExist());
    }

    @Test
    void documentsPhotoPreviewAsBinaryPngRatherThanBase64() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/workshop/paint-pots/{paintPotId}/photo-preview'].post.responses['200'].content['image/png'].schema.format").value("binary"));
    }

    @Test
    void publishesSearchHalAndValidationThroughTheRealSpringAdapter() throws Exception {
        mvc.perform(post("/api/v1/market/paint-products/search").contentType("application/json")
                .content("""
                        {"query":"karak","include":["suggestions"],"suggestionLimit":1}
                        """).accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").doesNotExist())
                .andExpect(jsonPath("$.suggestions.length()").value(1))
                .andExpect(jsonPath("$.suggestions[0]._links.paint-product.href").exists())
                .andExpect(jsonPath("$._links.facets.href").exists());
        mvc.perform(post("/api/v1/market/paint-products/search").contentType("application/json")
                .content("{\"query\":\"" + "x".repeat(201) + "\"}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("invalid_input"));
        mvc.perform(post("/api/v1/workshop/paint-stocks/search").contentType("application/json")
                .content("""
                        {"query":"karak","include":["suggestions"],"suggestionLimit":21}
                        """)).andExpect(status().isUnprocessableEntity());
        for (var body : java.util.List.of("{\"aggs\":{}}", "{\"filters\":{\"unknown\":[\"x\"]}}")) {
            mvc.perform(post("/api/v1/market/paint-products/search").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_json"));
        }
    }



    @Test
    void combinedSearchKeepsPagingAndSuggestionsInOneReadResponse() throws Exception {
        mvc.perform(post("/api/v1/workshop/paint-stocks/search").queryParam("size", "1")
                .contentType("application/json").header("X-Correlation-Id", "combined-http")
                .content("""
                        {"query":"karak","include":["results","suggestions"],"filters":{"range":["Warhammer Colour::Layer"]}}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.content[0].paintProduct.name").value("Karak Stone"))
                .andExpect(jsonPath("$.suggestions[0].name").value("Karak Stone"))
                .andExpect(jsonPath("$.suggestions[0]._links.paint-pots.href").exists())
                .andExpect(jsonPath("$.correlationId").value("combined-http"))
                .andExpect(jsonPath("$._links.first.href").value(org.hamcrest.Matchers.containsString("/search?page=0&size=1")));
    }

    @Test
    void registersSpringPagingAndPublishesTheGeneratedOpenApiContract() throws Exception {
        var specification = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        specification.andExpect(jsonPath("$.paths['/api/v1/market/paint-usage-guides'].get.operationId").value("searchPaintUsageGuides"))
                .andExpect(jsonPath("$.paths['/api/v1/market/paint-usage-guides/{paintUsageGuideId}'].get.operationId").value("getPaintUsageGuide"));
        mvc.perform(get("/api/v1/market/paint-usage-guides").queryParam("language", "fr").queryParam("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.guides").isArray()).andExpect(jsonPath("$._links.first.href").exists());
        for (var path : java.util.List.of("/api/v1/market/paint-products/search", "/api/v1/workshop/paint-stocks/search")) {
            var responses = "$.paths['" + path + "'].post.responses";
            specification.andExpect(jsonPath(responses + "['200'].content['application/json'].schema['$ref']").exists())
                    .andExpect(jsonPath(responses + "['200'].content['application/hal+json'].schema['$ref']").exists());
            for (var code : java.util.List.of("400", "422", "503")) {
                specification.andExpect(jsonPath(responses + "['" + code + "'].content['application/problem+json'].schema['$ref']")
                        .value("#/components/schemas/ProblemDetail"));
            }
        }
        mvc.perform(post("/api/v1/market/paint-products/search").contentType("application/json").content("{}").queryParam("page", "0").queryParam("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.content.length()").value(2))
                .andExpect(jsonPath("$.results.content[0].quantity").doesNotExist())
                .andExpect(jsonPath("$._links.self.href").exists())
                .andExpect(jsonPath("$._links.first.href").exists())
                .andExpect(jsonPath("$._links.last.href").exists());
        mvc.perform(post("/api/v1/workshop/paint-stocks/search").contentType("application/json").content("{}").queryParam("page", "0").queryParam("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.content").isArray())
                .andExpect(jsonPath("$.results.size").value(2))
                .andExpect(jsonPath("$.paintProducts").doesNotExist())
                .andExpect(jsonPath("$._links.facets.href").value("/api/v1/workshop/paint-stocks/facets"));
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.paths['/api/v1/market/paint-products/search'].post.operationId").value("searchPaintProducts"))
                .andExpect(jsonPath("$.paths['/api/v1/workshop/paint-stocks/search'].post.operationId").value("searchWorkshopPaintStocks"))
                .andExpect(jsonPath("$.paths['/api/v1/workshop/painting-project-import-previews/{paintableProductId}']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/workshop/paint-stocks/search']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/market/paint-catalog-editions']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/market/paint-catalog-editions/{id}']").exists())
                .andExpect(jsonPath("$.components.schemas.PaintModelSchemaResponse.properties['x-model-version']").exists());
        mvc.perform(get("/api/v1/market/paint-catalog-editions").queryParam("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.self.href").exists())
                .andExpect(jsonPath("$._links.first.href").exists())
                .andExpect(jsonPath("$._links.last.href").exists());
        mvc.perform(get("/api/v1/market/paint-product-model"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.additionalProperties").value(false))
                .andExpect(jsonPath("$.properties.length()").value(32))
                .andExpect(jsonPath("$.properties.catalog_memberships.items.required.length()").value(3))
                .andExpect(jsonPath("$['x-sort-options'].length()").value(11));
        var quality = market.paintProductQuality();
        assertEquals(market.paintProductFacets(com.minipaintdex.application.query.SearchPaintProductsQuery.empty(), false, false).total(), quality.total());
        assertEquals(quality.total(), quality.imageQualities().stream().mapToLong(row -> row.count()).sum());
        mvc.perform(get("/api/v1/market/paint-products/quality"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(quality.total()))
                .andExpect(jsonPath("$.imageQualities.length()").value(quality.imageQualities().size()))
                .andExpect(jsonPath("$.imageLimitations.length()").value(quality.imageLimitations().size()));
    }

    @Test
    void publishesOneUbiquitousLanguageWithoutLegacyApiAliases() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/workshop/paintables/{workshopPaintableId}']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/workshop/shopping-list/entries/{shoppingListEntryId}/checked']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/workshop/paint-pots/{paintPotId}']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/workshop/paint-pot-imports']").exists())
                .andExpect(jsonPath("$.components.schemas.PaintPotView.properties.paintProductId").exists())
                .andExpect(jsonPath("$.components.schemas.PaintProductView").exists())
                .andExpect(jsonPath("$.components.schemas.MarketPaintView").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/market/paint-products']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/workshop/paint-stocks']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/market/paint-products/suggestions']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/workshop/paint-stocks/suggestions']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/workshop/items']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/workshop/paints']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/shopping/items']").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.WorkshopPaintStockView.properties.paintProduct").exists())
                .andExpect(jsonPath("$.components.schemas.WorkshopPaintView").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.PaintingProjectView.properties.paintingProjectId").exists())
                .andExpect(jsonPath("$.components.schemas.PaintingProjectView.properties.paintableProductId").exists())
                .andExpect(jsonPath("$.components.schemas.PaintingProjectView.properties.productId").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.PaintableProductView.properties.paintableComponents").exists())
                .andExpect(jsonPath("$.components.schemas.PaintableProductView.properties.items").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.CreateWorkshopRecipeRequest.properties.paintableComponentId").exists())
                .andExpect(jsonPath("$.components.schemas.RecipeSolutionRequest.properties.paintProductId").exists());
        mvc.perform(get("/api/v1/workshop/paintables"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paintables").isArray())
                .andExpect(jsonPath("$.items").doesNotExist());
        mvc.perform(get("/api/v1/workshop/shopping-list/entries"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.entries").isArray())
                .andExpect(jsonPath("$.items").doesNotExist());
    }

    @Test
    void rejectsNonPositiveLifecycleDurations() {
        var storage = new MiniPaintDexProperties.Storage(
                Path.of("site"), Path.of("paints"), Path.of("shopping"),
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
        assertEquals(198, product.paintableComponents().stream().mapToInt(item -> item.quantity()).sum());
        assertTrue(market.paintProductFacets(
                        com.minipaintdex.application.query.SearchPaintProductsQuery.empty(), false, false)
                .total() > 1_500);
    }
}
