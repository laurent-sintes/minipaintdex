package com.minipaintdex.adapter.springevents;

import com.minipaintdex.application.event.CommittedEventBatch;
import com.minipaintdex.application.event.EventBatch;
import com.minipaintdex.application.event.EventPublication;
import com.minipaintdex.application.event.EventBusState;
import com.minipaintdex.application.event.EventPublicationStatus;
import com.minipaintdex.application.event.PublicationReceipt;
import com.minipaintdex.application.port.CommittedEventPublisher;
import com.minipaintdex.application.port.EventBus;
import com.minipaintdex.application.port.EventPublicationStore;
import com.minipaintdex.application.port.EventSubscriber;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Spring Events adapter with durable acceptance and one ordered critical consumer. */
public final class SpringEventBus implements EventBus, ApplicationListener<PublicationAvailable>, SmartLifecycle {
    private final ApplicationEventPublisher springEvents;
    private final EventPublicationStore store;
    private final EventSubscriber subscriber;
    private final CommittedEventPublisher committedEvents;
    private final EventBusSettings settings;
    private final ThreadPoolTaskExecutor dispatcher;
    private final ScheduledExecutorService retryScheduler;
    private final Set<String> scheduled = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean accepting = new AtomicBoolean();
    private final Object completionMonitor = new Object();

    public SpringEventBus(
            ApplicationEventPublisher springEvents,
            EventPublicationStore store,
            EventSubscriber subscriber,
            CommittedEventPublisher committedEvents,
            EventBusSettings settings) {
        this.springEvents = springEvents;
        this.store = store;
        this.subscriber = subscriber;
        this.committedEvents = committedEvents;
        this.settings = settings;
        dispatcher = new ThreadPoolTaskExecutor();
        dispatcher.setThreadNamePrefix("minipaintdex-ledger-");
        dispatcher.setCorePoolSize(settings.workerCount());
        dispatcher.setMaxPoolSize(settings.workerCount());
        dispatcher.setQueueCapacity(settings.queueCapacity());
        dispatcher.setWaitForTasksToCompleteOnShutdown(true);
        dispatcher.setAwaitTerminationMillis(settings.shutdownTimeout().toMillis());
        dispatcher.initialize();
        retryScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            var thread = new Thread(runnable, "minipaintdex-event-retry");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public PublicationReceipt publish(EventBatch batch) {
        if (!accepting.get()) throw new IllegalStateException("The event bus is not accepting publications.");
        var publication = store.savePending(batch);
        springEvents.publishEvent(new PublicationAvailable(publication.publicationId()));
        return new PublicationReceipt(
                publication.publicationId(), publication.status(), publication.createdAt(), batch.correlationId());
    }

    @Override
    public Optional<EventPublication> publication(String publicationId) {
        return store.find(publicationId);
    }

    @Override
    public EventPublication await(String publicationId, Duration timeout) throws InterruptedException {
        var deadline = System.nanoTime() + timeout.toNanos();
        synchronized (completionMonitor) {
            while (true) {
                var publication = store.find(publicationId).orElseThrow(() ->
                        new IllegalArgumentException("Unknown event publication: " + publicationId));
                if (terminal(publication)) return publication;
                var remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new IllegalStateException(new TimeoutException(
                        "Timed out waiting for event publication " + publicationId));
                TimeUnit.NANOSECONDS.timedWait(completionMonitor, remaining);
            }
        }
    }

    @Override
    public EventBusState state() {
        return new EventBusState(running.get(), accepting.get(), store.recoverable().size());
    }

    @Override
    public void onApplicationEvent(PublicationAvailable event) {
        schedule(event.publicationId());
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) return;
        accepting.set(true);
        store.recoverable().stream()
                .filter(publication -> publication.attempts() < settings.maxAttempts())
                .forEach(publication -> schedule(publication.publicationId()));
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        accepting.set(false);
        retryScheduler.shutdown();
        dispatcher.shutdown();
        var deadline = System.nanoTime() + settings.shutdownTimeout().toNanos();
        awaitDispatcher(deadline);
        // A rejected or delayed durable publication is drained synchronously after intake closes.
        while (System.nanoTime() < deadline) {
            var next = store.recoverable().stream()
                    .filter(publication -> publication.attempts() < settings.maxAttempts())
                    .findFirst().orElse(null);
            if (next == null) break;
            process(next.publicationId(), false);
        }
        synchronized (completionMonitor) {
            completionMonitor.notifyAll();
        }
    }

    @Override
    public void stop(Runnable callback) {
        try {
            stop();
        } finally {
            callback.run();
        }
    }

    @Override public boolean isRunning() { return running.get(); }
    @Override public boolean isAutoStartup() { return true; }
    @Override public int getPhase() { return Integer.MAX_VALUE - 100; }

    private void schedule(String publicationId) {
        if (!running.get() || !scheduled.add(publicationId)) return;
        try {
            dispatcher.execute(() -> process(publicationId, true));
        } catch (RejectedExecutionException rejected) {
            scheduled.remove(publicationId);
            retryLater(publicationId);
        }
    }

    private void process(String publicationId, boolean retryOnFailure) {
        var retry = false;
        try {
            var current = store.find(publicationId).orElse(null);
            if (current == null || current.status() == EventPublicationStatus.COMPLETED) return;
            var processing = store.markProcessing(publicationId, Instant.now());
            subscriber.consume(processing.batch());
            var completed = store.markCompleted(publicationId, Instant.now());
            committedEvents.publish(new CommittedEventBatch(completed.batch(), completed.updatedAt()));
        } catch (RuntimeException failure) {
            var failed = store.markFailed(publicationId, Instant.now(), safeMessage(failure));
            retry = retryOnFailure && running.get() && failed.attempts() < settings.maxAttempts();
        } finally {
            scheduled.remove(publicationId);
            synchronized (completionMonitor) {
                completionMonitor.notifyAll();
            }
            if (retry) retryLater(publicationId);
        }
    }

    private void retryLater(String publicationId) {
        if (!running.get() || retryScheduler.isShutdown()) return;
        try {
            retryScheduler.schedule(
                    () -> springEvents.publishEvent(new PublicationAvailable(publicationId)),
                    settings.retryDelay().toMillis(), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException ignored) {
            // Shutdown performs a final synchronous drain from the durable publication store.
        }
    }

    private void awaitDispatcher(long deadline) {
        var executor = dispatcher.getThreadPoolExecutor();
        if (executor == null) return;
        try {
            var remaining = deadline - System.nanoTime();
            if (remaining > 0) executor.awaitTermination(remaining, TimeUnit.NANOSECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean terminal(EventPublication publication) {
        return publication.status() == EventPublicationStatus.COMPLETED
                || publication.status() == EventPublicationStatus.FAILED
                && publication.attempts() >= settings.maxAttempts();
    }

    private static String safeMessage(RuntimeException failure) {
        var message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
