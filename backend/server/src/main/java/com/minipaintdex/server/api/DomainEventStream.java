package com.minipaintdex.server.api;

import com.minipaintdex.application.event.CommittedEventBatch;
import com.minipaintdex.bootstrap.MiniPaintDexProperties;
import org.springframework.context.event.EventListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;

@Component
final class DomainEventStream implements AutoCloseable {
    private final int replayCapacity;
    private final long connectionTimeoutMillis;
    private final ArrayDeque<Notification> replay;
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ThreadPoolExecutor sender;
    private final Object lifecycleMonitor = new Object();
    private volatile boolean closing;

    DomainEventStream(MiniPaintDexProperties properties) {
        var web = properties.web();
        replayCapacity = web.sseReplayCapacity();
        connectionTimeoutMillis = web.sseConnectionTimeout().toMillis();
        replay = new ArrayDeque<>(replayCapacity);
        sender = new ThreadPoolExecutor(
            1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(web.sseQueueCapacity()), runnable -> {
                var thread = new Thread(runnable, "minipaintdex-sse");
                thread.setDaemon(true);
                return thread;
            });
    }

    SseEmitter subscribe(String lastEventId) {
        synchronized (lifecycleMonitor) {
            if (closing) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Event stream is shutting down.");
            var emitter = new SseEmitter(connectionTimeoutMillis);
            emitter.onCompletion(() -> emitters.remove(emitter));
            emitter.onTimeout(() -> emitters.remove(emitter));
            emitter.onError(ignored -> emitters.remove(emitter));
            emitters.add(emitter);
            try {
                sender.execute(() -> { replay(emitter, lastEventId); sendHeartbeat(emitter); });
            } catch (RejectedExecutionException failure) {
                emitters.remove(emitter);
                emitter.complete();
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Event stream is busy.");
            }
            return emitter;
        }
    }

    @EventListener
    void committed(CommittedEventBatch committed) {
        enqueue(notification(committed));
    }

    @Scheduled(fixedDelayString = "${minipaintdex.web.sse-heartbeat}")
    void heartbeat() {
        if (closing) return;
        try {
            sender.execute(() -> emitters.forEach(emitter -> sendHeartbeat(emitter)));
        } catch (RejectedExecutionException ignored) {
            // Heartbeats are disposable when the bounded sender is busy or closing.
        }
    }

    @EventListener(ContextClosedEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    void contextClosing() {
        // ContextClosedEvent precedes lifecycle shutdown: close long-lived HTTP requests before
        // Tomcat starts graceful draining. Bean destruction alone is too late (after its timeout).
        close();
    }

    @Override
    public void close() {
        List<SseEmitter> active;
        synchronized (lifecycleMonitor) {
            if (closing) return;
            closing = true;
            active = List.copyOf(emitters);
            emitters.clear();
            sender.shutdownNow();
        }
        active.forEach(SseEmitter::complete);
    }

    private void enqueue(Notification notification) {
        if (closing) return;
        synchronized (replay) {
            if (replay.size() == replayCapacity) replay.removeFirst();
            replay.addLast(notification);
        }
        try {
            sender.execute(() -> emitters.forEach(emitter -> send(emitter, notification)));
        } catch (RuntimeException ignored) {
            // The client will resynchronize through REST when the bounded notification queue is saturated.
        }
    }

    private void replay(SseEmitter emitter, String lastEventId) {
        if (closing) return;
        List<Notification> snapshot;
        synchronized (replay) {
            snapshot = new ArrayList<>(replay);
        }
        if (lastEventId == null || lastEventId.isBlank()) return;
        var position = -1;
        for (var index = 0; index < snapshot.size(); index++) {
            if (lastEventId.equals(snapshot.get(index).id())) position = index;
        }
        if (position < 0 && !snapshot.isEmpty()) {
            try {
                emitter.send(SseEmitter.event().name("resync-required").data("{}"));
            } catch (IOException | IllegalStateException failure) {
                remove(emitter, failure);
            }
            return;
        }
        for (var index = position + 1; index < snapshot.size(); index++) send(emitter, snapshot.get(index));
    }

    private void send(SseEmitter emitter, Notification notification) {
        if (closing) return;
        try {
            emitter.send(SseEmitter.event()
                    .id(notification.id())
                    .name("domain-events-committed")
                    .data(notification));
        } catch (IOException | IllegalStateException failure) {
            remove(emitter, failure);
        }
    }

    private void sendHeartbeat(SseEmitter emitter) {
        if (closing) return;
        try {
            emitter.send(SseEmitter.event().comment("heartbeat"));
        } catch (IOException | IllegalStateException failure) {
            remove(emitter, failure);
        }
    }

    private void remove(SseEmitter emitter, Throwable failure) {
        emitters.remove(emitter);
        emitter.completeWithError(failure);
    }

    private static Notification notification(CommittedEventBatch committed) {
        var events = committed.batch().events();
        var eventTypes = events.stream().map(event -> event.eventType()).distinct().toList();
        var aggregateTypes = events.stream().map(event -> event.aggregateType()).distinct().toList();
        var aggregateIds = events.stream().map(event -> event.aggregateId())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        var projectIds = events.stream().map(event -> event.scopePaintingProjectId())
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return new Notification(
                committed.batch().batchId(), committed.batch().correlationId(), events.size(),
                eventTypes, aggregateTypes, List.copyOf(aggregateIds), List.copyOf(projectIds),
                committed.committedAt());
    }

    record Notification(
            String id,
            String correlationId,
            int eventCount,
            List<String> eventTypes,
            List<String> aggregateTypes,
            List<String> aggregateIds,
            List<String> projectIds,
            Instant committedAt) {
    }
}
