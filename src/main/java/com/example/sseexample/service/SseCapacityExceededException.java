package com.example.sseexample.service;

/**
 * Raised when the global SSE connection cap ({@code app.sse.max-connections}) is
 * already reached. Surfaced to clients as 503 with a Retry-After header.
 */
public class SseCapacityExceededException extends RuntimeException {

    public SseCapacityExceededException(int maxConnections) {
        super("SSE connection limit reached (" + maxConnections + ")");
    }
}
