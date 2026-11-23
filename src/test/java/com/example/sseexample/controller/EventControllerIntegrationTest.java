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

    private org.springframework.test.web.servlet.ResultActions postTrigger(String body) throws Exception {
        return mockMvc.perform(post("/api/trigger-event")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private void assertNothingBroadcast() {
        org.junit.jupiter.api.Assertions.assertNull(eventService.getLastEventName());
        org.junit.jupiter.api.Assertions.assertNull(eventService.getLastEventData());
    }

    // The documented body is a JSON string. It used to be passed on with its quotes
    // still on and was then JSON-encoded a second time (issue #65).
    @Test
    void triggerEvent_WithJsonString_ShouldBroadcastDecodedMessage() throws Exception {
        postTrigger("\"Test message\"")
                .andExpect(status().isOk())
                .andExpect(content().string("Event triggered"));

        org.junit.jupiter.api.Assertions.assertEquals("custom", eventService.getLastEventName());
        org.junit.jupiter.api.Assertions.assertEquals("Test message", eventService.getLastEventData());
    }

    @Test
    void triggerEvent_WithEscapedCharacters_ShouldBroadcastThemUnescaped() throws Exception {
        postTrigger("\"say \\\"hi\\\"\\nbye\"")
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals("say \"hi\"\nbye", eventService.getLastEventData());
    }

    @Test
    void triggerEvent_WithMalformedJson_ShouldReturn400() throws Exception {
        postTrigger("invalid json")
                .andExpect(status().isBadRequest());

        assertNothingBroadcast();
    }

    @Test
    void triggerEvent_WithJsonObject_ShouldReturn400() throws Exception {
        postTrigger("{\"message\":\"hello\"}")
                .andExpect(status().isBadRequest());

        assertNothingBroadcast();
    }

    @Test
    void triggerEvent_WithJsonNumber_ShouldReturn400() throws Exception {
        postTrigger("42")
                .andExpect(status().isBadRequest());

        assertNothingBroadcast();
    }

    @Test
    void triggerEvent_WithBlankMessage_ShouldReturn400() throws Exception {
        postTrigger("\"   \"")
                .andExpect(status().isBadRequest());

        assertNothingBroadcast();
    }

    @Test
    void triggerEvent_AtMaximumLength_ShouldBroadcast() throws Exception {
        String message = "x".repeat(EventController.MAX_MESSAGE_LENGTH);

        postTrigger("\"" + message + "\"")
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals(message, eventService.getLastEventData());
    }

    @Test
    void triggerEvent_OverMaximumLength_ShouldReturn400() throws Exception {
        String message = "x".repeat(EventController.MAX_MESSAGE_LENGTH + 1);

        postTrigger("\"" + message + "\"")
                .andExpect(status().isBadRequest());

        assertNothingBroadcast();
    }

    @Test
    void triggerEvent_WithPlainTextContentType_ShouldReturn415() throws Exception {
        mockMvc.perform(post("/api/trigger-event")
                .contentType(MediaType.TEXT_PLAIN)
                .content("hello"))
                .andExpect(status().isUnsupportedMediaType());

        assertNothingBroadcast();
    }

    @Test
    void home_ShouldReturnWelcomeMessage() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("SSE Example Server is running")));
    }
}
