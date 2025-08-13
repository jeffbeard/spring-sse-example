package com.example.sseexample;

import com.example.sseexample.service.EventService;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.*;

class SimpleTest {

    @Test
    void eventService_ShouldCreateEmitter() {
        // Given
        EventService service = new EventService(false);
        
        // When
        SseEmitter emitter = service.createEventStream();
        
        // Then
        assertNotNull(emitter);
        assertEquals(0L, emitter.getTimeout());
    }

    @Test
    void eventService_ShouldBroadcastWithoutError() {
        // Given
        EventService service = new EventService(false);
        
        // When & Then
        assertDoesNotThrow(() -> {
            service.broadcastEvent("test", "message");
        });
    }

    @Test
    void basicTest() {
        assertTrue(true);
    }
}