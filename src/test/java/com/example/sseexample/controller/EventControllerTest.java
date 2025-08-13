package com.example.sseexample.controller;

import com.example.sseexample.service.EventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class EventControllerTest {

    @Mock
    private EventService eventService;

    private EventController eventController;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        eventController = new EventController(eventService);
        mockMvc = MockMvcBuilders.standaloneSetup(eventController).build();
    }

    @Test
    void streamEvents_ShouldReturnSseEmitter() {
        // Given
        SseEmitter mockEmitter = new SseEmitter();
        when(eventService.createEventStream()).thenReturn(mockEmitter);

        // When
        SseEmitter result = eventController.streamEvents();

        // Then
        assertNotNull(result);
        assertEquals(mockEmitter, result);
        verify(eventService).createEventStream();
    }

    @Test
    void triggerEvent_ShouldCallEventServiceAndReturnOk() throws Exception {
        // Given
        String message = "Test message";

        // When & Then
        mockMvc.perform(post("/api/trigger-event")
                .contentType("application/json")
                .content("\"" + message + "\""))
                .andExpect(status().isOk())
                .andExpect(content().string("Event triggered"));

        verify(eventService).broadcastEvent(eq("custom"), eq(message));
    }

    @Test
    void triggerEvent_WithEmptyMessage_ShouldStillSucceed() throws Exception {
        // Given
        String message = "";

        // When & Then
        mockMvc.perform(post("/api/trigger-event")
                .contentType("application/json")
                .content("\"" + message + "\""))
                .andExpect(status().isOk())
                .andExpect(content().string("Event triggered"));

        verify(eventService).broadcastEvent(eq("custom"), eq(message));
    }

    @Test
    void home_ShouldReturnWelcomeMessage() {
        // When
        ResponseEntity<String> response = eventController.home();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("SSE Example Server is running! Visit /test.html to see SSE in action.", 
                    response.getBody());
    }

    @Test
    void triggerEvent_ShouldHandleNullMessage() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/trigger-event")
                .contentType("application/json")
                .content("null"))
                .andExpect(status().isOk())
                .andExpect(content().string("Event triggered"));

        verify(eventService).broadcastEvent(eq("custom"), isNull());
    }
}