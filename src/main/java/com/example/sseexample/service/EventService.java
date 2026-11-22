package com.example.sseexample.service;

import com.example.sseexample.config.SseProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class EventService {

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler =
        Executors.newScheduledThreadPool(1, namedDaemonFactory("sse-scheduler"));
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Object admissionLock = new Object();
    private final SseProperties properties;
    private final ExecutorService broadcastExecutor;
    private final MeterRegistry meterRegistry;
    private final Counter connectionsOpened;
    private final Counter connectionsRejected;
    private final Counter connectionsDropped;

    @Autowired
    public EventService(SseProperties properties, MeterRegistry meterRegistry) {
        this(properties, meterRegistry, true);
    }

    /** For tests that do not look at metrics: meters go to a throwaway registry. */
    EventService(SseProperties properties, boolean enablePeriodicEvents) {
        this(properties, new SimpleMeterRegistry(), enablePeriodicEvents);
    }

    EventService(SseProperties properties, MeterRegistry meterRegistry, boolean enablePeriodicEvents) {
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.broadcastExecutor = Executors.newFixedThreadPool(
            properties.broadcastThreads(), namedDaemonFactory("sse-broadcast"));

        // The HTTP server metrics record a stream only when it closes, so they cannot
        // show how many are open. These gauges read the live values on every scrape.
        Gauge.builder("sse.connections.active", emitters, List::size)
            .description("SSE connections currently open")
            .register(meterRegistry);
        Gauge.builder("sse.connections.max", properties, SseProperties::maxConnections)
            .description("Configured cap on concurrent SSE connections")
            .register(meterRegistry);
        this.connectionsOpened = Counter.builder("sse.connections.opened")
            .description("SSE connections admitted")
            .register(meterRegistry);
        this.connectionsRejected = Counter.builder("sse.connections.rejected")
            .description("SSE connections refused because the cap was reached")
            .register(meterRegistry);
        this.connectionsDropped = Counter.builder("sse.connections.dropped")
            .description("SSE connections removed after a failed or timed-out send")
            .register(meterRegistry);

        if (enablePeriodicEvents) {
            startPeriodicEvents();
        }
    }

    public EventService(boolean enablePeriodicEvents) {
        this(SseProperties.defaults(), enablePeriodicEvents);
    }

    public SseEmitter createEventStream() {
        SseEmitter emitter = new SseEmitter(properties.timeoutMs());

        emitter.onCompletion(() -> releaseEmitter(emitter));
        emitter.onTimeout(() -> releaseEmitter(emitter));
        emitter.onError((e) -> releaseEmitter(emitter));

        admit(emitter);

        try {
            emitter.send(SseEmitter.event()
                .name("connected")
                .data("Connected to SSE stream at " + getCurrentTimestamp()));
            recordEventSent("connected");
        } catch (IOException e) {
            releaseEmitter(emitter);
        }

        return emitter;
    }

    /**
     * Admits an emitter if the connection cap allows it. The capacity check and the
     * insertion share a lock: without it, concurrent requests all observe the same
     * pre-cap size and the limit is exceeded.
     */
    void admit(SseEmitter emitter) {
        synchronized (admissionLock) {
            if (emitters.size() >= properties.maxConnections()) {
                connectionsRejected.increment();
                throw new SseCapacityExceededException(properties.maxConnections());
            }
            emitters.add(emitter);
        }
        connectionsOpened.increment();
    }

    /**
     * Releases a connection slot. All three emitter callbacks may fire for the same
     * emitter, so removal is idempotent by construction: CopyOnWriteArrayList.remove
     * returns true for exactly one caller.
     */
    boolean releaseEmitter(SseEmitter emitter) {
        return emitters.remove(emitter);
    }

    /**
     * Fans the event out to every emitter on a bounded executor, giving each send a
     * fixed budget. A consumer that cannot keep up is dropped instead of stalling
     * delivery to everyone else.
     */
    public void broadcastEvent(String eventName, String data) {
        String eventData = buildPayload(data);
        List<SseEmitter> targets = new ArrayList<>(emitters);
        if (targets.isEmpty()) {
            return;
        }

        List<AtomicBoolean> started = new ArrayList<>(targets.size());
        List<Future<?>> sends = new ArrayList<>(targets.size());

        for (SseEmitter emitter : targets) {
            AtomicBoolean sendStarted = new AtomicBoolean(false);
            started.add(sendStarted);
            try {
                sends.add(broadcastExecutor.submit(() -> {
                    sendStarted.set(true);
                    emitter.send(SseEmitter.event().name(eventName).data(eventData));
                    return null;
                }));
            } catch (RejectedExecutionException e) {
                // Executor is shutting down; nothing further to deliver.
                sends.add(null);
            }
        }

        for (int i = 0; i < targets.size(); i++) {
            Future<?> send = sends.get(i);
            if (send == null) {
                continue;
            }
            SseEmitter emitter = targets.get(i);
            try {
                send.get(properties.sendTimeoutMs(), TimeUnit.MILLISECONDS);
                recordEventSent(eventName);
            } catch (TimeoutException e) {
                send.cancel(true);
                // Only the emitter whose send actually began is at fault. A task still
                // queued behind a slow consumer is starved, not slow, so it is left
                // registered and retried on the next broadcast.
                if (started.get(i).get()) {
                    dropEmitter(emitter, e);
                }
            } catch (ExecutionException e) {
                dropEmitter(emitter, e.getCause() != null ? e.getCause() : e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * Counts one delivery to one connection. The event name becomes a tag, so it must
     * stay a small fixed set (connected, heartbeat, notification, custom): every
     * distinct value is a separate time series. Never pass client-supplied text here.
     */
    private void recordEventSent(String eventName) {
        meterRegistry.counter("sse.events.sent", "event", eventName).increment();
    }

    private void dropEmitter(SseEmitter emitter, Throwable cause) {
        if (releaseEmitter(emitter)) {
            connectionsDropped.increment();
            try {
                emitter.completeWithError(cause);
            } catch (RuntimeException ignored) {
                // Emitter may already be closed by the container.
            }
        }
    }

    private void startPeriodicEvents() {
        scheduler.scheduleAtFixedRate(() -> {
            broadcastEvent("heartbeat", "Server heartbeat");
        }, 0, 30, TimeUnit.SECONDS);

        scheduler.scheduleAtFixedRate(() -> {
            String[] sampleMessages = {
                "New user registered",
                "Order processed successfully",
                "System maintenance scheduled",
                "Database backup completed",
                "New feature deployed"
            };
            String randomMessage =
                sampleMessages[ThreadLocalRandom.current().nextInt(sampleMessages.length)];
            broadcastEvent("notification", randomMessage);
        }, 10, 15, TimeUnit.SECONDS);
    }

    /**
     * Stops both executors and releases every emitter. Without this the scheduler
     * outlives the application context and leaks across test runs (issue #26).
     */
    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
        broadcastExecutor.shutdownNow();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.complete();
            } catch (RuntimeException ignored) {
                // Emitter may already be closed by the container.
            }
        }
        emitters.clear();
    }

    /** Waits for both executors to terminate. Returns false if they are still running. */
    boolean awaitTermination(long timeoutMs) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        boolean schedulerDone = scheduler.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS);
        long remainingMs = Math.max(0, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
        boolean broadcastDone = broadcastExecutor.awaitTermination(remainingMs, TimeUnit.MILLISECONDS);
        return schedulerDone && broadcastDone;
    }

    private static ThreadFactory namedDaemonFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    String buildPayload(String message) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("message", message);
        payload.put("timestamp", getCurrentTimestamp());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            // String values cannot fail serialization; fall back defensively.
            return "{\"message\":null,\"timestamp\":null}";
        }
    }

    private String getCurrentTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}