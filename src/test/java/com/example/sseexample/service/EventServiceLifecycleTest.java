package com.example.sseexample.service;

import com.example.sseexample.config.SseProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers scheduler and executor shutdown for issue #26.
 */
class EventServiceLifecycleTest {

    @Test
    void shutdown_StopsSchedulerAndBroadcastExecutor() throws Exception {
        EventService service = new EventService(SseProperties.defaults(), true);

        service.shutdown();

        assertTrue(service.awaitTermination(5_000), "executors should terminate after shutdown");
    }

    @Test
    void shutdown_ReleasesRegisteredEmitters() throws Exception {
        EventService service = new EventService(SseProperties.defaults(), false);
        SseEmitter emitter = service.createEventStream();

        service.shutdown();

        assertFalse(service.releaseEmitter(emitter), "shutdown should have cleared registered emitters");
    }
}
