package com.minipaintdex.server.api;

import com.minipaintdex.bootstrap.MiniPaintDexProperties;
import org.junit.jupiter.api.Test;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

class DomainEventStreamTest {
    @Test
    void closesOpenHttpStreamsBeforeEvenTheFirstLifecycleShutdownPhase() throws Exception {
        var stream = stream();
        var mvc = MockMvcBuilders.standaloneSetup(new DomainEventStreamController(stream)).build();
        var request = mvc.perform(get("/api/v1/events")).andExpect(request().asyncStarted()).andReturn();
        var phaseObserved = new AtomicBoolean();
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(DomainEventStream.class, () -> stream);
            context.registerBean("shutdownProbe", SmartLifecycle.class, () -> new SmartLifecycle() {
                private boolean running;
                public void start() { running = true; }
                public boolean isRunning() { return running; }
                public int getPhase() { return Integer.MAX_VALUE; }
                public void stop() {
                    // Completion must precede HTTP graceful draining, not merely bean destruction.
                    assertNull(request.getAsyncResult(1000));
                    assertEquals(503, assertThrows(ResponseStatusException.class,
                            () -> stream.subscribe(null)).getStatusCode().value());
                    phaseObserved.set(true);
                    running = false;
                }
            });
            context.refresh();
        }
        assertTrue(phaseObserved.get());
    }

    @Test
    void shutdownIsIdempotentAndLateHeartbeatsDoNotSubmitToTheClosedExecutor() {
        var stream = stream();
        stream.subscribe(null);
        stream.close();
        assertDoesNotThrow(stream::close);
        assertDoesNotThrow(stream::heartbeat);
        assertEquals(503, assertThrows(ResponseStatusException.class,
                () -> stream.subscribe(null)).getStatusCode().value());
    }

    private static DomainEventStream stream() {
        var properties = mock(MiniPaintDexProperties.class);
        when(properties.web()).thenReturn(new MiniPaintDexProperties.Web(
                List.of("http://127.0.0.1:8080"), 16, 16, Duration.ofMinutes(30), Duration.ofSeconds(15)));
        return new DomainEventStream(properties);
    }
}
