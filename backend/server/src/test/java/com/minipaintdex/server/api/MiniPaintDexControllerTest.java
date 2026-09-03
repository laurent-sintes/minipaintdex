package com.minipaintdex.server.api;

import com.minipaintdex.application.usecase.AdministrationUseCases;
import com.minipaintdex.application.MarketCatalogApplicationService;
import com.minipaintdex.application.port.MarketCatalogReader;
import com.minipaintdex.application.usecase.MarketCatalogUseCases;
import com.minipaintdex.application.usecase.SiteQueries;
import com.minipaintdex.application.usecase.WorkshopUseCases;
import com.minipaintdex.application.command.ApplyPaintProductChangeSetCommand;
import com.minipaintdex.application.command.CreateWorkshopRecipeCommand;
import com.minipaintdex.application.command.CreatePaintingProjectCommand;
import com.minipaintdex.application.query.SearchPaintProductsQuery;
import com.minipaintdex.application.query.PageQuery;
import com.minipaintdex.application.result.PageResult;
import com.minipaintdex.application.result.ApplyPaintProductChangeSetResult;
import com.minipaintdex.application.result.CreatePaintingProjectResult;
import com.minipaintdex.application.event.EventPublicationStatus;
import com.minipaintdex.application.event.PublicationReceipt;
import com.minipaintdex.application.view.PaintProductView;
import com.minipaintdex.application.view.PaintableProductSummaryView;
import com.minipaintdex.application.view.WorkshopOverviewView;
import com.minipaintdex.application.view.GuideReconciliationView;
import com.minipaintdex.application.view.MarketPaintingGuideView;
import com.minipaintdex.application.view.WorkshopPaintStockView;
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
        mvc = MockMvcBuilders.standaloneSetup(new PaintUsageGuideController(market),
                        new SiteController(site),
                        new MarketCatalogController(market),
                        new PaintCatalogEditionController(market),
                        new AdministrationController(administration),
                        new WorkshopController(workshop), new PaintPotController(workshop))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void exposesBoundedSharedGuidesWithExplicitLanguageAndProductScope() throws Exception {
        when(market.searchPaintUsageGuides(any())).thenReturn(new com.minipaintdex.application.result.PaintUsageGuidesResult(new PageResult<>(List.of(), 0, 2, 0), "test"));
        mvc.perform(get("/api/v1/market/paint-usage-guides").queryParam("paintProductId", "paint").queryParam("language", "original")
                .queryParam("size", "2").header("X-Correlation-Id", "test"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.guides").isArray()).andExpect(jsonPath("$.correlationId").value("test"));
        var capture = ArgumentCaptor.forClass(com.minipaintdex.application.query.SearchPaintUsageGuidesQuery.class);
        verify(market).searchPaintUsageGuides(capture.capture());
        assertEquals("paint", capture.getValue().paintProductId()); assertEquals("original", capture.getValue().language());
        mvc.perform(get("/api/v1/market/paint-usage-guides").queryParam("language", "de")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void readsRepresentativePhotoWithoutMutatingTheCatalog() throws Exception {
        var photo = new WorkshopPaintStockView.PersonalPhoto("pot-one", "photo-one", "/cutout.png", "/original.png", "test-cutout", "My pot", Instant.parse("2026-09-01T00:00:00Z"));
        when(workshop.getWorkshopPaintStock(any())).thenReturn(new com.minipaintdex.application.result.WorkshopPaintStockResult(
                new WorkshopPaintStockView(paint("paint", "Paint"), 1, 1, photo, true), "photo-read"));
        mvc.perform(get("/api/v1/workshop/paint-stocks/paint").header("X-Correlation-Id", "photo-read"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stock.personalPhoto.url").value("/cutout.png"))
                .andExpect(jsonPath("$.stock.personalPhoto.originalUrl").value("/original.png"))
                .andExpect(jsonPath("$.stock.personalPhoto.paintPotId").value("pot-one"))
                .andExpect(jsonPath("$.stock.canReplacePhoto").value(true))
                .andExpect(jsonPath("$.correlationId").value("photo-read"));
        var capture = ArgumentCaptor.forClass(com.minipaintdex.application.query.GetWorkshopPaintStockQuery.class);
        verify(workshop).getWorkshopPaintStock(capture.capture());
        assertEquals("paint", capture.getValue().paintProductId());
        assertEquals("photo-read", capture.getValue().correlationId());
        mvc.perform(get("/api/v1/workshop/paint-stocks/Bad-ID")).andExpect(status().isUnprocessableEntity());
        when(workshop.getWorkshopPaintStock(any())).thenThrow(new com.minipaintdex.domain.shared.DomainException("not_found", "Unknown paint product"));
        mvc.perform(get("/api/v1/workshop/paint-stocks/missing")).andExpect(status().isNotFound());
    }

    @Test
    void previewsBinaryPhotosAndForwardsAttachmentChoiceWithIdempotency() throws Exception {
        var file = new org.springframework.mock.web.MockMultipartFile("file", "pot.png", "image/png", new byte[]{1, 2, 3});
        when(workshop.previewPaintPotPhoto(any())).thenReturn(new com.minipaintdex.application.result.PaintPotPhotoPreview(new byte[]{4, 5, 6}, "test-cutout", "preview"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/workshop/paint-pots/pot-one/photo-preview")
                        .file(file).header("X-Correlation-Id", "preview"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("image/png"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(new byte[]{4, 5, 6}))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"));
        var preview = ArgumentCaptor.forClass(com.minipaintdex.application.query.PreviewPaintPotPhotoQuery.class);
        verify(workshop).previewPaintPotPhoto(preview.capture());
        assertEquals("pot-one", preview.getValue().paintPotId());
        assertEquals("preview", preview.getValue().correlationId());
        when(workshop.addPaintPotPhoto(any())).thenReturn(new PublicationReceipt("pub-photo", EventPublicationStatus.PENDING, Instant.now(), "upload"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/workshop/paint-pots/pot-one/photos")
                        .file(file).param("removeBackground", "true").header("Idempotency-Key", "photo-key"))
                .andExpect(status().isAccepted());
        var capture = ArgumentCaptor.forClass(com.minipaintdex.application.command.AddPaintPotPhotoCommand.class);
        verify(workshop).addPaintPotPhoto(capture.capture());
        org.junit.jupiter.api.Assertions.assertTrue(capture.getValue().removeBackground());
        assertEquals("photo-key", capture.getValue().idempotencyKey());
        when(workshop.previewPaintPotPhoto(any())).thenThrow(new DomainException("photo_processing_unavailable", "Disabled"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/workshop/paint-pots/pot-one/photo-preview").file(file))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("photo_processing_unavailable"));
    }

    @Test
    void searchEndpointsSelectSuggestionsWithLinksAndFilters() throws Exception {
        var suggestions = List.of(new com.minipaintdex.application.view.PaintProductSuggestion("karak", "Karak Stone", "Citadel", "Layer", "22-17", "", ""));
        when(market.searchPaintProducts(any(com.minipaintdex.application.query.PaintSearchQuery.class)))
                .thenReturn(new com.minipaintdex.application.result.PaintSearchResult<>(null, suggestions, "test"));
        when(workshop.searchWorkshopPaintStocks(any()))
                .thenReturn(new com.minipaintdex.application.result.PaintSearchResult<>(null, suggestions, "test"));
        for (var path : List.of("/api/v1/market/paint-products/search", "/api/v1/workshop/paint-stocks/search")) {
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).header("X-Correlation-Id", "test")
                    .content("""
                        {"query":"kar","include":["suggestions"],"suggestionLimit":3,
                         "filters":{"brand":["A","B"],"range":["Citadel::Layer"],"manufacturerSheetOnly":true}}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[0].paintProductId").value("karak"))
                .andExpect(jsonPath("$.suggestions[0].range").value("Layer"))
                .andExpect(jsonPath("$.results").doesNotExist())
                .andExpect(jsonPath("$.correlationId").value("test"));
        }
        var captor = ArgumentCaptor.forClass(com.minipaintdex.application.query.PaintSearchQuery.class);
        verify(market).searchPaintProducts(captor.capture());
        assertEquals(3, captor.getValue().suggestionLimit());
        assertEquals(List.of("A", "B"), captor.getValue().filters().brand());
        assertEquals("Citadel::Layer", captor.getValue().filters().range().getFirst().selectionKey());
        assertEquals(true, captor.getValue().manufacturerSheetOnly());
        verify(workshop).searchWorkshopPaintStocks(captor.capture());
        assertEquals("kar", captor.getValue().filters().query());
    }

    @Test
    void searchRejectsInvalidSelectionAndDoesNotHideFailures() throws Exception {
        for (var body : List.of("{\"suggestionLimit\":0}", "{\"include\":[]}", "{\"include\":[\"aggs\"]}")) {
            mvc.perform(post("/api/v1/market/paint-products/search").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnprocessableEntity());
        }
        org.mockito.Mockito.verifyNoInteractions(market);
        when(market.searchPaintProducts(any(com.minipaintdex.application.query.PaintSearchQuery.class)))
                .thenThrow(new DomainException("search_unavailable", "Unavailable"));
        mvc.perform(post("/api/v1/market/paint-products/search").contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"kar\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("search_unavailable"));
    }

    @Test
    void registersAndObservesPotsThroughTheWorkshopPort() throws Exception {
        var receipt = new PublicationReceipt("publication-pot", EventPublicationStatus.PENDING, Instant.parse("2026-09-01T12:00:00Z"), "pot-request");
        when(workshop.registerPaintPot(any())).thenReturn(new com.minipaintdex.application.result.ImportPaintPotsResult(1, 0, true, receipt));
        when(workshop.observePaintPot(any())).thenReturn(receipt);
        mvc.perform(post("/api/v1/workshop/paint-pots").contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "pot-add")
                .content("{\"paintPotId\":\"pot-one\",\"paintProductId\":\"paint-red\"}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.result.added").value(1));
        var registration = ArgumentCaptor.forClass(com.minipaintdex.application.command.RegisterPaintPotCommand.class);
        verify(workshop).registerPaintPot(registration.capture());
        assertEquals("paint-red", registration.getValue().paintProductId());
        assertEquals("pot-add", registration.getValue().idempotencyKey());
        mvc.perform(post("/api/v1/workshop/paint-pots/pot-one/observations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"condition\":\"thickened\",\"remainingLevel\":\"low\"}"))
                .andExpect(status().isAccepted());
        var observation = ArgumentCaptor.forClass(com.minipaintdex.application.command.ObservePaintPotCommand.class);
        verify(workshop).observePaintPot(observation.capture());
        assertEquals("pot-one", observation.getValue().paintPotId());
        assertEquals("low", observation.getValue().remainingLevel());
    }

    @Test
    void exposesSourcedEditionPagesAndDetails() throws Exception {
        var edition = new com.minipaintdex.domain.market.paint.PaintCatalogEdition(1, "brand-2019", "Brand", "Catalogue",
                "2019", 2019, List.of("Range"), List.of(java.net.URI.create("https://example.com/catalog.pdf")));
        when(market.searchPaintCatalogEditions(any())).thenReturn(new PageResult<>(List.of(edition), 0, 1, 2));
        when(market.getPaintCatalogEdition(any())).thenReturn(edition);
        mvc.perform(get("/api/v1/market/paint-catalog-editions").param("brand", "Brand").param("size", "1")
                        .header("X-Correlation-Id", "edition-test"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.editions[0].id").value("brand-2019"))
                .andExpect(jsonPath("$.totalPages").value(2));
        var query = ArgumentCaptor.forClass(com.minipaintdex.application.query.SearchPaintCatalogEditionsQuery.class);
        verify(market).searchPaintCatalogEditions(query.capture());
        assertEquals("Brand", query.getValue().brand());
        assertEquals("edition-test", query.getValue().correlationId());
        mvc.perform(get("/api/v1/market/paint-catalog-editions/brand-2019"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.publicationYear").value(2019));
        var controller = new PaintCatalogEditionController(market);
        assertEquals("/api/v1/market/paint-catalog-editions/brand-2019",
                controller.edition("brand-2019", "test").getRequiredLink("self").getHref());
    }

    @Test
    void forwardsAllMarketSearchFacetsToTheApplication() throws Exception {
        when(market.searchPaintProducts(any(com.minipaintdex.application.query.PaintSearchQuery.class)))
                .thenReturn(new com.minipaintdex.application.result.PaintSearchResult<>(
                        new PageResult<>(List.of(paint("paint", "Paint")), 2, 10, 21), null, "test"));
        mvc.perform(post("/api/v1/market/paint-products/search").queryParam("page", "2").queryParam("size", "10")
                        .queryParam("sort", "brand,desc").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"filters":{"finish":["matte"],"brand":["Vallejo","AK Interactive"],
                            "range":["Warhammer Colour::Contrast","Warhammer Colour::Layer"],"color":["blue","red"],
                            "coverage":["transparent"],"applicationSystem":["one_coat_shading"],"effect":["metallic"]}}
                            """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results.content[0].id").value("paint"));
        var capture = ArgumentCaptor.forClass(com.minipaintdex.application.query.PaintSearchQuery.class);
        verify(market).searchPaintProducts(capture.capture());
        var query = capture.getValue().filters();
        var page = capture.getValue().page();
        assertEquals(List.of("matte"), query.finish());
        assertEquals(List.of("Vallejo", "AK Interactive"), query.brand());
        assertEquals(List.of("blue", "red"), query.color());
        assertEquals(List.of("Warhammer Colour::Contrast", "Warhammer Colour::Layer"),
                query.range().stream().map(com.minipaintdex.application.query.PaintRangeSelection::selectionKey).toList());
        assertEquals(List.of("transparent"), query.coverage());
        assertEquals(List.of("one_coat_shading"), query.applicationSystem());
        assertEquals(List.of("metallic"), query.effect());
        assertEquals(2, page.page()); assertEquals(10, page.size()); assertEquals("brand", page.sort().getFirst().property());
    }

    @Test
    void rejectsAnUnqualifiedRangeBeforeInvokingTheApplication() throws Exception {
        mvc.perform(post("/api/v1/market/paint-products/search").contentType(MediaType.APPLICATION_JSON)
                .content("{\"filters\":{\"range\":[\"Contrast\"]}}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("invalid_input"));
        org.mockito.Mockito.verifyNoInteractions(market);
    }

    @Test
    void publishesTheCanonicalPaintModelAsJsonSchema() throws Exception {
        when(market.paintProductModel()).thenReturn(
                new MarketCatalogApplicationService(mock(MarketCatalogReader.class), mock(com.minipaintdex.application.port.PaintProductSearchIndex.class),
                        new com.minipaintdex.application.query.PaintSearchPolicy(8, 20, 200, 16, 5, 1, 50, 2000, 1000, 8, 3, 1, 0.8f, 0.2f)).paintProductModel());

        mvc.perform(get("/api/v1/market/paint-product-model"))
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
                .andExpect(jsonPath("$['x-filters'][0].control").value("checkbox"))
                .andExpect(jsonPath("$['x-filters'][0].group").value("primary"))
                .andExpect(jsonPath("$['x-filters'][12].control").value("toggle"))
                .andExpect(jsonPath("$['x-sort-options'][0].queryValue").value("relevance,desc"))
                .andExpect(jsonPath("$['x-vocabularies']['paint-role'][1]").value("primer"));
    }

    @Test
    void exposesOwnedPaintsOnlyThroughTheWorkshopContext() throws Exception {
        when(workshop.searchWorkshopPaintStocks(any())).thenReturn(new com.minipaintdex.application.result.PaintSearchResult<>(
                new PageResult<>(List.of(new WorkshopPaintStockView(paint("paint", "Paint"), 2, 2, null, true)), 0, 10, 1), null, "test"));
        mvc.perform(post("/api/v1/workshop/paint-stocks/search").queryParam("page", "0").queryParam("size", "10")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results.content[0].paintProduct.id").value("paint"))
                .andExpect(jsonPath("$.results.content[0].quantity").value(2));
        verify(workshop).searchWorkshopPaintStocks(any());
    }

    @Test
    void exposesPaintChangeSetDryRuns() throws Exception {
        when(administration.applyPaintProductChangeSet(any())).thenReturn(
                new ApplyPaintProductChangeSetResult(1, 0, 0, 0, 0, 0, 1, 48, false));

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

        var command = ArgumentCaptor.forClass(ApplyPaintProductChangeSetCommand.class);
        verify(administration).applyPaintProductChangeSet(command.capture());
        assertEquals(true, command.getValue().dryRun());
    }

    @Test
    void forwardsEditionOnlyChangeSetsWithoutInventoryOperations() throws Exception {
        when(administration.applyPaintProductChangeSet(any())).thenReturn(
                new ApplyPaintProductChangeSetResult(0, 0, 0, 0, 0, 0, 0, 48, false));
        mvc.perform(post("/api/v1/market/paint-changesets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schema_version":1,"kind":"market_paints","operations":[],
                                 "catalog_editions":[{"schema_version":1,"id":"brand-2019","brand":"Brand",
                                 "title":"Catalogue","edition_label":"2019","ranges":["Range"],
                                 "source_urls":["https://example.com/catalog.pdf"]}]}
                                """))
                .andExpect(status().isOk());
        var command = ArgumentCaptor.forClass(ApplyPaintProductChangeSetCommand.class);
        verify(administration).applyPaintProductChangeSet(command.capture());
        assertEquals(true, command.getValue().dryRun());
        assertEquals(0, command.getValue().operations().size());
        assertEquals(new com.minipaintdex.application.document.StructuredDocument.Text("brand-2019"),
                command.getValue().catalogEditions().getFirst().fields().stream()
                        .filter(field -> field.name().equals("id")).findFirst().orElseThrow().value());
    }

    @Test
    void exposesQualityReviewConflictsWithoutApplyingTheChangeSet() throws Exception {
        when(administration.applyPaintProductChangeSet(any())).thenThrow(
                new com.minipaintdex.domain.shared.DomainException("conflict", "Paint quality change requires an explicit before/after review: paint/color.hex"));
        mvc.perform(post("/api/v1/market/paint-changesets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schema_version":1,"kind":"market_paints","operations":[{"action":"upsert","record":{"id":"paint"}}]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Paint quality change requires an explicit before/after review: paint/color.hex"));
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
                .andExpect(jsonPath("$.result.workshopPaintablesAdded").value(198));

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
                                  "recipeId": "recipe-1",
                                  "paintableComponentId": "game-hero",
                                  "displayName": "My hero",
                                  "version": 1,
                                  "solutions": [
                                    {"type": "single_paint", "paintProductId": "paint-1"}
                                  ]
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.publication.publicationId").value("publication-recipe"));

        var command = ArgumentCaptor.forClass(CreateWorkshopRecipeCommand.class);
        verify(workshop).createWorkshopRecipe(command.capture());
        assertEquals("game-hero", command.getValue().paintableComponentId());
        assertEquals("recipe-create", command.getValue().idempotencyKey());
    }

    @Test
    void translatesDomainFailuresToProblemDetails() throws Exception {
        when(market.getPaintProduct("missing")).thenThrow(
                new DomainException("not_found", "Paint not found: missing"));

        mvc.perform(get("/api/v1/market/paint-products/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:minipaintdex:problem:not_found"))
                .andExpect(jsonPath("$.code").value("not_found"));
    }

    private static PublicationReceipt receipt(String id) {
        return new PublicationReceipt(id, EventPublicationStatus.PENDING,
                Instant.parse("2026-08-30T10:00:00Z"), "correlation");
    }

    static PaintProductView paint(String id, String name) {
        return new PaintProductView(
                id, "Brand", "Manufacturer", List.of(), "Range",
                new PaintProductView.Profile(
                        List.of("color_paint"), List.of("brush"), "conventional_layering",
                        "opaque", "matte", List.of(), "any", false, "acrylic"),
                "", name, "#000000", "current", "confirmed", "", List.of(),
                "", "", "", "", "", "", "", "none", 6, "", "", "", "", 18, "Black", "", List.of(),
                new PaintProductView.UsageInstructions("", List.of(), List.of(), "", false),
                "", "", "", "", "", "", List.of(), java.util.List.of());
    }
}
