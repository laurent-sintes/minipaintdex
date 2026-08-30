package com.minipaintdex.server.api;

import com.minipaintdex.application.MiniPaintDexService;
import com.minipaintdex.application.command.ApplyMarketPaintChangeSetCommand;
import com.minipaintdex.application.command.CreateWorkshopRecipeCommand;
import com.minipaintdex.application.query.SearchMarketPaintsQuery;
import com.minipaintdex.application.result.ApplyMarketPaintChangeSetResult;
import com.minipaintdex.domain.event.DomainEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MiniPaintDexControllerTest {
    private MiniPaintDexService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(MiniPaintDexService.class);
        mvc = MockMvcBuilders.standaloneSetup(new MiniPaintDexController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void forwardsAllMarketSearchFacetsToTheApplication() throws Exception {
        when(service.searchMarketPaints(any())).thenReturn(List.of(Map.of("id", "paint")));

        mvc.perform(get("/api/v1/market/paints")
                        .queryParam("finish", "matt")
                        .queryParam("opacity", "transparent")
                        .queryParam("manufacturer", "Games Workshop")
                        .queryParam("tag", "cold"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paints[0].id").value("paint"));

        var query = ArgumentCaptor.forClass(SearchMarketPaintsQuery.class);
        verify(service).searchMarketPaints(query.capture());
        assertEquals("matt", query.getValue().finish());
        assertEquals("transparent", query.getValue().opacity());
        assertEquals("Games Workshop", query.getValue().manufacturer());
        assertEquals("cold", query.getValue().tag());
    }

    @Test
    void exposesPaintChangeSetDryRuns() throws Exception {
        when(service.applyMarketPaintChangeSet(any())).thenReturn(
                new ApplyMarketPaintChangeSetResult(1, 0, 0, 0, 0, 1, 48, false));

        mvc.perform(post("/api/v1/market/paint-changesets")
                        .queryParam("dryRun", "true")
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
        verify(service).applyMarketPaintChangeSet(command.capture());
        assertEquals(true, command.getValue().dryRun());
    }

    @Test
    void exposesOwnedPaintReconciliationForAMarketGuide() throws Exception {
        when(service.reconcileMarketPaintingGuide("guide-1")).thenReturn(Map.of(
                "guide", Map.of("id", "guide-1"), "slots", List.of(), "ownedPaintCount", 47));

        mvc.perform(get("/api/v1/market/painting-guides/guide-1/reconciliation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guide.id").value("guide-1"))
                .andExpect(jsonPath("$.ownedPaintCount").value(47));
    }

    @Test
    void createsAWorkshopRecipeThroughTheApplicationService() throws Exception {
        when(service.createWorkshopRecipe(any())).thenReturn(mock(DomainEvent.class));
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
                .andExpect(status().isCreated());

        var command = ArgumentCaptor.forClass(CreateWorkshopRecipeCommand.class);
        verify(service).createWorkshopRecipe(command.capture());
        assertEquals("game-hero", command.getValue().catalogItemId());
        assertEquals("recipe-create", command.getValue().idempotencyKey());
    }
}
