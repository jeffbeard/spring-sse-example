package com.example.sseexample.controller;

import com.example.sseexample.service.EventService;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.*;

class EventControllerSimpleTest {

    @Test
    void controller_ShouldCreateWithService() {
        // Given
        EventService service = new EventService(false);
        
        // When
        EventController controller = new EventController(service);
        
        // Then
        assertNotNull(controller);
    }
    
    @Test
    void streamEvents_ShouldReturnEmitter() {
        // Given
        EventService service = new EventService(false);
        EventController controller = new EventController(service);
        
        // When
        SseEmitter emitter = controller.streamEvents();
        
        // Then
        assertNotNull(emitter);
    }
    
    @Test
    void home_ShouldReturnMessage() {
        // Given
        EventService service = new EventService(false);
        EventController controller = new EventController(service);
        
        // When
        var response = controller.home();
        
        // Then
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("SSE Example Server is running"));
    }
    
    @Test
    void triggerEvent_ShouldReturnSuccess() {
        // Given
        EventService service = new EventService(false);
        EventController controller = new EventController(service);
        
        // When
        var response = controller.triggerEvent("test message");
        
        // Then
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("Event triggered", response.getBody());
    }
}