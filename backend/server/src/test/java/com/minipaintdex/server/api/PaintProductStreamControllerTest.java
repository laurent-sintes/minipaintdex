package com.minipaintdex.server.api;

import com.minipaintdex.application.query.SearchPaintProductsQuery;
import com.minipaintdex.application.usecase.MarketCatalogUseCases;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaintProductStreamControllerTest {
    @Test
    void writesOneJsonDocumentPerLineFromTheLazyApplicationStream() throws Exception {
        var market = mock(MarketCatalogUseCases.class);
        when(market.streamPaintProducts(any(SearchPaintProductsQuery.class))).thenReturn(Stream.of(
                MiniPaintDexControllerTest.paint("paint-1", "One"),
                MiniPaintDexControllerTest.paint("paint-2", "Two")));
        var body = new PaintProductStreamController(market).stream(
                null, null, null, null, null, null, null, null, null, null, null, null, null);
        var output = new ByteArrayOutputStream();

        body.writeTo(output);

        var lines = output.toString(StandardCharsets.UTF_8).strip().split("\\R");
        assertEquals(2, lines.length);
        org.junit.jupiter.api.Assertions.assertTrue(lines[0].contains("\"id\":\"paint-1\""));
        org.junit.jupiter.api.Assertions.assertTrue(lines[1].contains("\"id\":\"paint-2\""));
    }
}
