package com.example.sseexample.service;

import com.example.sseexample.config.SseProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Autowired
    public EventService(SseProperties properties) {
        this(properties, true);
    }

    EventService(SseProperties properties, boolean enablePeriodicEvents) {
        this.properties = properties;
        this.broadcastExecutor = Executors.newFixedThreadPool(
            properties.broadcastThreads(), namedDaemonFactory("sse-broadcast"));
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
                throw new SseCapacityExceededException(properties.maxConnections());
            }
            emitters.add(emitter);
        }
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

    private void dropEmitter(SseEmitter emitter, Throwable cause) {
        if (releaseEmitter(emitter)) {
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
        } catch (JsonProcessingException e) {
            // String values cannot fail serialization; fall back defensively.
            return "{\"message\":null,\"timestamp\":null}";
        }
    }

    private String getCurrentTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}