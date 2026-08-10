package com.example.sseexample.controller;

import com.example.sseexample.service.EventService;
import com.example.sseexample.service.SseCapacityExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EventControllerIntegrationTest {

    private MockMvc mockMvc;
    private TestEventService eventService;
    private EventController eventController;

    @BeforeEach
    void setUp() {
        eventService = new TestEventService();
        eventController = new EventController(eventService);
        mockMvc = MockMvcBuilders.standaloneSetup(eventController).build();
    }

    // Simple test implementation of EventService that doesn't need to be mocked
    static class TestEventService extends EventService {
        private SseEmitter lastEmitter;
        private String lastEventName;
        private String lastEventData;

        public TestEventService() {
            super(false); // Disable periodic events
        }

        @Override
        public SseEmitter createEventStream() {
            lastEmitter = new SseEmitter(0L);
            return lastEmitter;
        }

        @Override
        public void broadcastEvent(String eventName, String data) {
            this.lastEventName = eventName;
            this.lastEventData = data;
        }

        public SseEmitter getLastEmitter() {
            return lastEmitter;
        }

        public String getLastEventName() {
            return lastEventName;
        }

        public String getLastEventData() {
            return lastEventData;
        }
    }

    // Stands in for a service that has hit the app.sse.max-connections cap (issue #15)
    static class SaturatedEventService extends EventService {
        public SaturatedEventService() {
            super(false);
        }

        @Override
        public SseEmitter createEventStream() {
            throw new SseCapacityExceededException(1);
        }
    }

    @Test
    void streamEvents_AtCapacity_ShouldReturn503WithRetryAfter() throws Exception {
        MockMvc saturated = MockMvcBuilders
            .standaloneSetup(new EventController(new SaturatedEventService()))
            .build();

        saturated.perform(get("/api/events")
                .accept(MediaType.TEXT_EVENT_STREAM_VALUE))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"));
    }

    @Test
    void streamEvents_ShouldReturnSseEmitter() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/events")
                .accept(MediaType.TEXT_EVENT_STREAM_VALUE))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        // Verify that an emitter was created
        org.junit.jupiter.api.Assertions.assertNotNull(eventService.getLastEmitter());
    }

    @Test
    void triggerEvent_ShouldBroadcastEvent() throws Exception {
        // Given
        String testMessage = "Test message";

        // When
        mockMvc.perform(post("/api/trigger-event")
                .contentType(MediaType.APPLICATION_JSON)
                .content("\"" + testMessage + "\""))
                .andExpect(status().isOk())
                .andExpect(content().string("Event triggered"));

        // Then
        org.junit.jupiter.api.Assertions.assertEquals("custom", eventService.getLastEventName());
        // The message is wrapped in quotes because it's a JSON string
        org.junit.jupiter.api.Assertions.assertEquals("\"" + testMessage + "\"", eventService.getLastEventData());
    }

    @Test
    void home_ShouldReturnWelcomeMessage() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("SSE Example Server is running")));
    }

    @Test
    void triggerEvent_WithInvalidJson_ShouldStillAcceptIt() throws Exception {
        // Given
        String invalidJson = "invalid json";

        // When
        mockMvc.perform(post("/api/trigger-event")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
                .andExpect(status().isOk())
                .andExpect(content().string("Event triggered"));

        // Then - Verify that the broadcast happened with the raw invalid JSON
        org.junit.jupiter.api.Assertions.assertEquals("custom", eventService.getLastEventName());
        org.junit.jupiter.api.Assertions.assertEquals(invalidJson, eventService.getLastEventData());
    }
}
