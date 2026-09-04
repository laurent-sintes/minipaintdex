package com.minipaintdex.server.api;
import com.minipaintdex.application.usecase.*;
import com.minipaintdex.application.storage.StorageContracts.*;
import com.minipaintdex.application.result.*;
import com.minipaintdex.application.event.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import java.time.Instant;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RackControllerTest {
    @Test void pagesMarketAndWorkshopAndAcceptsTypedConfirmation() throws Exception {
        var market = mock(MarketCatalogUseCases.class);
        var workshop = mock(WorkshopUseCases.class);
        var admin = mock(AdministrationUseCases.class);
        when(market.searchContainerFormats(any())).thenReturn(new RackCatalogPage<>(new PageResult<>(List.of(), 0, 2, 0), 3, "test"));
        when(workshop.listWorkshopRacks(any())).thenReturn(new PageResult<>(List.of(), 0, 2, 0));
        when(workshop.confirmPaintStorage(any())).thenReturn(new PublicationReceipt("receipt", EventPublicationStatus.PENDING, Instant.EPOCH, "test"));
        when(workshop.addWorkshopRacks(any())).thenReturn(new PublicationReceipt("receipt", EventPublicationStatus.PENDING, Instant.EPOCH, "test"));
        var mvc = MockMvcBuilders.standaloneSetup(new MarketRackController(market, admin), new WorkshopRackController(workshop))
                .setControllerAdvice(new ApiExceptionHandler()).setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver()).build();
        mvc.perform(get("/api/v1/market/container-formats?size=2").header("X-Correlation-Id", "test"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.catalogRevision").value(3)).andExpect(jsonPath("$.results.content").isArray())
                .andExpect(jsonPath("$.links").isArray());
        mvc.perform(get("/api/v1/workshop/racks?size=2")).andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
        mvc.perform(post("/api/v1/workshop/paint-storage/confirmations").contentType("application/json")
                .header("Idempotency-Key", "confirm-one").header("X-Correlation-Id", "test")
                .content("{\"snapshotToken\":\"abc\",\"placements\":[],\"allowEstimates\":true}"))
                .andExpect(status().isAccepted()).andExpect(header().string("Location", "/api/v1/publications/receipt"));
        var capture = ArgumentCaptor.forClass(Confirm.class); verify(workshop).confirmPaintStorage(capture.capture());
        assertEquals("confirm-one", capture.getValue().idempotencyKey()); assertEquals("abc", capture.getValue().snapshotToken());
        assertTrue(capture.getValue().allowEstimates());
        mvc.perform(post("/api/v1/workshop/rack-acquisitions").contentType("application/json").header("Idempotency-Key", "add-two")
                .content("{\"rackProductId\":\"wood\",\"quantity\":2,\"location\":\"Desk\"}"))
                .andExpect(status().isAccepted());
        var acquisition = ArgumentCaptor.forClass(AddRacks.class); verify(workshop).addWorkshopRacks(acquisition.capture());
        assertEquals(2, acquisition.getValue().quantity()); assertEquals("wood", acquisition.getValue().rackProductId());
        assertEquals("add-two", acquisition.getValue().idempotencyKey());
        mvc.perform(post("/api/v1/workshop/rack-acquisitions").contentType("application/json").header("Idempotency-Key", "zero")
                .content("{\"rackProductId\":\"wood\",\"quantity\":0}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/workshop/paint-storage/confirmations").contentType("application/json")
                .content("{\"snapshotToken\":\"abc\",\"placements\":[]}")).andExpect(status().isBadRequest());
    }
}
