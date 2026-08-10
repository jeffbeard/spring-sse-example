package com.example.sseexample.service;

import com.example.sseexample.config.SseProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the connection cap and finite emitter timeout added for issue #15.
 */
class EventServiceLimitsTest {

    private static SseProperties props(int maxConnections) {
        return new SseProperties(maxConnections, 300_000L, 5_000L, 4);
    }

    @Test
    void createEventStream_UsesConfiguredTimeoutInsteadOfInfinite() {
        EventService service = new EventService(props(10), false);

        SseEmitter emitter = service.createEventStream();

        assertEquals(300_000L, emitter.getTimeout());
    }

    @Test
    void createEventStream_AtCapacity_ThrowsCapacityExceeded() {
        EventService service = new EventService(props(2), false);
        service.createEventStream();
        service.createEventStream();

        assertThrows(SseCapacityExceededException.class, service::createEventStream);
    }

    @Test
    void createEventStream_AfterEmitterReleased_AdmitsAnotherConnection() {
        EventService service = new EventService(props(1), false);
        SseEmitter first = service.createEventStream();

        service.releaseEmitter(first);

        assertDoesNotThrow(service::createEventStream);
    }

    @Test
    void releaseEmitter_CalledTwice_DoesNotFreeTwoSlots() {
        EventService service = new EventService(props(1), false);
        SseEmitter first = service.createEventStream();

        service.releaseEmitter(first);
        service.releaseEmitter(first);
        service.createEventStream();

        assertThrows(SseCapacityExceededException.class, service::createEventStream);
    }
}
