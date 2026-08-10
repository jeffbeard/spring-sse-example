package com.example.sseexample.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tunable limits for the SSE endpoint (issue #15).
 *
 * @param maxConnections global ceiling on concurrent SSE connections
 * @param timeoutMs      emitter timeout; clients reconnect automatically when it elapses
 * @param sendTimeoutMs  budget for a single emitter send before the client is dropped
 * @param broadcastThreads size of the pool that fans a broadcast out to emitters
 */
@ConfigurationProperties(prefix = "app.sse")
public record SseProperties(
    @DefaultValue("1000") int maxConnections,
    @DefaultValue("300000") long timeoutMs,
    @DefaultValue("5000") long sendTimeoutMs,
    @DefaultValue("4") int broadcastThreads
) {

    public SseProperties {
        if (maxConnections < 1) {
            throw new IllegalArgumentException("app.sse.max-connections must be at least 1");
        }
        if (broadcastThreads < 1) {
            throw new IllegalArgumentException("app.sse.broadcast-threads must be at least 1");
        }
    }

    /** Defaults matching the annotated values, for contexts without property binding. */
    public static SseProperties defaults() {
        return new SseProperties(1000, 300_000L, 5_000L, 4);
    }
}
