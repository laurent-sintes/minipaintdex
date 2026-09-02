package com.minipaintdex.server.api;

import com.minipaintdex.application.usecase.AdministrationUseCases;
import com.minipaintdex.application.MarketCatalogApplicationService;
import com.minipaintdex.application.port.MarketCatalogReader;
import com.minipaintdex.application.usecase.MarketCatalogUseCases;
import com.minipaintdex.application.usecase.SiteQueries;
import com.minipaintdex.application.usecase.WorkshopUseCases;
import com.minipaintdex.application.command.ApplyMarketPaintChangeSetCommand;
import com.minipaintdex.application.command.CreateWorkshopRecipeCommand;
import com.minipaintdex.application.command.CreatePaintingProjectCommand;
import com.minipaintdex.application.query.SearchMarketPaintsQuery;
import com.minipaintdex.application.query.PageQuery;
import com.minipaintdex.application.result.PageResult;
import com.minipaintdex.application.result.ApplyMarketPaintChangeSetResult;
import com.minipaintdex.application.result.CreatePaintingProjectResult;
import com.minipaintdex.application.event.EventPublicationStatus;
import com.minipaintdex.application.event.PublicationReceipt;
import com.minipaintdex.application.view.MarketPaintView;
import com.minipaintdex.application.view.PaintableProductSummaryView;
import com.minipaintdex.application.view.WorkshopOverviewView;
import com.minipaintdex.application.view.GuideReconciliationView;
import com.minipaintdex.application.view.MarketPaintingGuideView;
import com.minipaintdex.application.view.WorkshopPaintView;
import com.minipaintdex.domain.shared.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;

import java.util.List;
import java.util.Map;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MiniPaintDexControllerTest {
    private MarketCatalogUseCases market;
    private WorkshopUseCases workshop;
    private AdministrationUseCases administration;
    private SiteQueries site;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        market = mock(MarketCatalogUseCases.class);
        workshop = mock(WorkshopUseCases.class);
        administration = mock(AdministrationUseCases.class);
        site = mock(SiteQueries.class);
        mvc = MockMvcBuilders.standaloneSetup(
                        new SiteController(site),
                        new MarketCatalogController(market),
                        new AdministrationController(administration),
                        new WorkshopController(workshop))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void forwardsAllMarketSearchFacetsToTheApplication() throws Exception {
        when(market.searchMarketPaintPage(any(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(new PageResult<>(List.of(paint("paint", "Paint")), 2, 10, 21));

        mvc.perform(get("/api/v1/market/paints")
                        .queryParam("page", "2")
                        .queryParam("size", "10")
                        .queryParam("sort", "brand,desc")
                        .queryParam("finish", "matte")
                        .queryParam("coverage", "transparent")
                        .queryParam("applicationSystem", "one_coat_shading")
                        .queryParam("effect", "metallic"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paints[0].id").value("paint"));

        var query = ArgumentCaptor.forClass(SearchMarketPaintsQuery.class);
        var page = ArgumentCaptor.forClass(PageQuery.class);
        verify(market).searchMarketPaintPage(query.capture(), anyBoolean(), anyBoolean(), page.capture());
        assertEquals("matte", query.getValue().finish());
        assertEquals("transparent", query.getValue().coverage());
        assertEquals("one_coat_shading", query.getValue().applicationSystem());
        assertEquals("metallic", query.getValue().effect());
        assertEquals(2, page.getValue().page());
        assertEquals(10, page.getValue().size());
        assertEquals("brand", page.getValue().sort().getFirst().property());
    }

    @Test
    void publishesTheCanonicalPaintModelAsJsonSchema() throws Exception {
        when(market.marketPaintModel()).thenReturn(
                new MarketCatalogApplicationService(mock(MarketCatalogReader.class)).marketPaintModel());

        mvc.perform(get("/api/v1/market/paint-model"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['$schema']").value("https://json-schema.org/draft/2020-12/schema"))
                .andExpect(jsonPath("$['x-model-version']").value(1))
                .andExpect(jsonPath("$['x-image-quality-ranks'].official_photo").value(1))
                .andExpect(jsonPath("$.additionalProperties").value(false))
                .andExpect(jsonPath("$.required[8]").value("manufacturer_image"))
                .andExpect(jsonPath("$.properties.source_snapshots.type").value("array"))
                .andExpect(jsonPath("$.properties.usage_instructions.type").value("object"))
                .andExpect(jsonPath("$.properties.manufacturer_image.properties.image_quality.enum[5]").value("none"))
                .andExpect(jsonPath("$.properties.manufacturer_image.required[0]").value("image_quality"))
                .andExpect(jsonPath("$.properties.manufacturer_image.properties.quality_limitation.required[0]").value("code"))
                .andExpect(jsonPath("$['x-vocabularies']['image-quality-limitation'][0]").value("official-photo-not-published"))
                .andExpect(jsonPath("$['x-filters'][0].queryParameter").value("role"))
                .andExpect(jsonPath("$['x-filters'][12].control").value("toggle"))
                .andExpect(jsonPath("$['x-sort-options'][0].queryValue").value("name,asc"))
                .andExpect(jsonPath("$['x-vocabularies']['paint-role'][1]").value("primer"));
    }

    @Test
    void exposesOwnedPaintsOnlyThroughTheWorkshopContext() throws Exception {
        when(workshop.searchWorkshopPaintPage(any(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(new PageResult<>(List.of(new WorkshopPaintView(paint("paint", "Paint"), 2)), 0, 10, 1));

        mvc.perform(get("/api/v1/workshop/paints").queryParam("page", "0").queryParam("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paints[0].marketPaint.id").value("paint"))
                .andExpect(jsonPath("$.paints[0].quantity").value(2));

        verify(workshop).searchWorkshopPaintPage(any(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    void exposesPaintChangeSetDryRuns() throws Exception {
        when(administration.applyMarketPaintChangeSet(any())).thenReturn(
                new ApplyMarketPaintChangeSetResult(1, 0, 0, 0, 0, 0, 1, 48, false));

        mvc.perform(post("/api/v1/market/paint-changesets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schema_version": 1,
                                  "kind": "market_paints",
                                  "operations": [
                                    {"action": "upsert", "record": {"id": "paint"}}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.added").value(1))
                .andExpect(jsonPath("$.result.applied").value(false));

        var command = ArgumentCaptor.forClass(ApplyMarketPaintChangeSetCommand.class);
        verify(administration).applyMarketPaintChangeSet(command.capture());
        assertEquals(true, command.getValue().dryRun());
    }

    @Test
    void exposesOwnedPaintReconciliationForAMarketGuide() throws Exception {
        when(workshop.reconcileMarketPaintingGuide("guide-1")).thenReturn(new GuideReconciliationView(
                new MarketPaintingGuideView("guide-1", "item-1", 1, "documented",
                        List.of(), List.of(), List.of(), List.of()),
                List.of(), 47));

        mvc.perform(get("/api/v1/workshop/painting-guide-reconciliations/guide-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guide.id").value("guide-1"))
                .andExpect(jsonPath("$.ownedPaintCount").value(47));
    }

    @Test
    void exposesMarketPaintableProductsSeparatelyFromTheWorkshop() throws Exception {
        when(market.listMarketPaintableProducts()).thenReturn(List.of(new PaintableProductSummaryView(
                "reichbusters-reloaded", "Reichbusters Reloaded", "Reichbusters", "board_game", "full_set",
                1, 198)));
        when(workshop.workshopOverview()).thenReturn(new WorkshopOverviewView(
                "my-workshop", List.of(), 1, 0, 0, 0, List.of()));

        mvc.perform(get("/api/v1/market/paintable-products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paintableProducts[0].id").value("reichbusters-reloaded"));
        mvc.perform(get("/api/v1/workshop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workshop.id").value("my-workshop"));
    }

    @Test
    void createsAPaintingProjectThroughTheWorkshopAggregate() throws Exception {
        when(workshop.createPaintingProject(any())).thenReturn(
                new CreatePaintingProjectResult(
                        "my-workshop", "paint-reichbusters", "reichbusters-reloaded", 198, 0, false, true,
                        receipt("publication-import")));

        mvc.perform(post("/api/v1/workshop/painting-projects")
                        .header("Idempotency-Key", "import-reichbusters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paintableProductId":"reichbusters-reloaded","paintingProjectId":"paint-reichbusters"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.result.workshopItemsAdded").value(198));

        var command = ArgumentCaptor.forClass(CreatePaintingProjectCommand.class);
        verify(workshop).createPaintingProject(command.capture());
        assertEquals("reichbusters-reloaded", command.getValue().paintableProductId());
        assertEquals("import-reichbusters", command.getValue().idempotencyKey());
    }

    @Test
    void createsAWorkshopRecipeThroughTheApplicationService() throws Exception {
        when(workshop.createWorkshopRecipe(any())).thenReturn(receipt("publication-recipe"));
        mvc.perform(post("/api/v1/workshop/recipes")
                        .header("Idempotency-Key", "recipe-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipe_id": "recipe-1",
                                  "catalog_item_id": "game-hero",
                                  "display_name": "My hero",
                                  "version": 1,
                                  "solutions": [
                                    {"type": "single_paint", "paint_id": "paint-1"}
                                  ]
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.publication.publicationId").value("publication-recipe"));

        var command = ArgumentCaptor.forClass(CreateWorkshopRecipeCommand.class);
        verify(workshop).createWorkshopRecipe(command.capture());
        assertEquals("game-hero", command.getValue().catalogItemId());
        assertEquals("recipe-create", command.getValue().idempotencyKey());
    }

    @Test
    void translatesDomainFailuresToProblemDetails() throws Exception {
        when(market.getMarketPaint("missing")).thenThrow(
                new DomainException("not_found", "Paint not found: missing"));

        mvc.perform(get("/api/v1/market/paints/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:minipaintdex:problem:not_found"))
                .andExpect(jsonPath("$.code").value("not_found"));
    }

    private static PublicationReceipt receipt(String id) {
        return new PublicationReceipt(id, EventPublicationStatus.PENDING,
                Instant.parse("2026-08-30T10:00:00Z"), "correlation");
    }

    static MarketPaintView paint(String id, String name) {
        return new MarketPaintView(
                id, "Brand", "Manufacturer", List.of(), "Range",
                new MarketPaintView.Profile(
                        List.of("color_paint"), List.of("brush"), "conventional_layering",
                        "opaque", "matte", List.of(), "any", false, "acrylic"),
                "", name, "#000000", "current", "confirmed", "", List.of(),
                "", "", "", "", "", "", "", "none", 6, "", "", "", "", 18, "Black", "", List.of(),
                new MarketPaintView.UsageInstructions("", List.of(), List.of(), "", false),
                "", "", "", "", "", "");
    }
}
