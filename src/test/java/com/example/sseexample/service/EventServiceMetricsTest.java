package com.example.sseexample.service;

import com.example.sseexample.config.SseProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The generic HTTP metrics record an SSE stream only once it closes, so they say
 * nothing about connections that are open right now. These meters do.
 */
class EventServiceMetricsTest {

    private final MeterRegistry registry = new SimpleMeterRegistry();
    private EventService service;

    private EventService service(int maxConnections) {
        service = new EventService(new SseProperties(maxConnections, 300_000L, 200L, 2), registry, false);
        return service;
    }

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.shutdown();
        }
    }

    private double gauge(String name) {
        return registry.get(name).gauge().value();
    }

    private double counter(String name, String... tags) {
        return registry.get(name).tags(tags).counter().count();
    }

    /** An emitter whose client has gone away: every send fails. */
    private static final class BrokenEmitter extends SseEmitter {
        @Override
        public void send(SseEventBuilder builder) throws IOException {
            throw new IOException("client went away");
        }
    }

    @Test
    void activeConnections_TracksOpenAndReleasedStreams() {
        EventService service = service(10);
        assertEquals(0, gauge("sse.connections.active"));

        SseEmitter first = service.createEventStream();
        service.createEventStream();
        assertEquals(2, gauge("sse.connections.active"));

        service.releaseEmitter(first);
        assertEquals(1, gauge("sse.connections.active"));
    }

    @Test
    void maxConnections_ReportsConfiguredCap() {
        service(7);

        assertEquals(7, gauge("sse.connections.max"));
    }

    @Test
    void openedConnections_CountsEveryAdmittedStream_AndDoesNotGoDownOnRelease() {
        EventService service = service(10);

        SseEmitter first = service.createEventStream();
        service.createEventStream();
        service.releaseEmitter(first);

        assertEquals(2, counter("sse.connections.opened"));
    }

    @Test
    void rejectedConnections_CountsStreamsRefusedAtCapacity() {
        EventService service = service(1);
        service.createEventStream();

        assertThrows(SseCapacityExceededException.class, service::createEventStream);
        assertThrows(SseCapacityExceededException.class, service::createEventStream);

        assertEquals(2, counter("sse.connections.rejected"));
        assertEquals(1, counter("sse.connections.opened"));
    }

    @Test
    void eventsSent_CountsOneDeliveryPerConnection_TaggedByEventName() {
        EventService service = service(10);
        service.createEventStream();
        service.createEventStream();

        service.broadcastEvent("custom", "hello");
        service.broadcastEvent("custom", "again");
        service.broadcastEvent("heartbeat", "tick");

        assertEquals(4, counter("sse.events.sent", "event", "custom"));
        assertEquals(2, counter("sse.events.sent", "event", "heartbeat"));
        // Each stream is greeted with a "connected" event when it opens.
        assertEquals(2, counter("sse.events.sent", "event", "connected"));
    }

    @Test
    void eventsSent_WithNoConnections_CountsNothing() {
        EventService service = service(10);

        service.broadcastEvent("custom", "nobody is listening");

        assertEquals(0, registry.find("sse.events.sent").tags("event", "custom").counters()
            .stream().mapToDouble(c -> c.count()).sum());
    }

    @Test
    void droppedConnections_CountsConsumersRemovedAfterAFailedSend() {
        EventService service = service(10);
        service.createEventStream();
        service.admit(new BrokenEmitter());

        service.broadcastEvent("custom", "hello");

        assertEquals(1, counter("sse.connections.dropped"));
        assertEquals(1, gauge("sse.connections.active"));
        // Only the healthy connection received it.
        assertEquals(1, counter("sse.events.sent", "event", "custom"));
    }
}
