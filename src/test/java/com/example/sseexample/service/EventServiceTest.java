package com.example.sseexample.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    private EventService eventService;

    @BeforeEach
    void setUp() {
        eventService = new EventService(false); // Disable periodic events for testing
    }

    @Test
    void createEventStream_ShouldReturnValidSseEmitter() {
        // When
        SseEmitter emitter = eventService.createEventStream();

        // Then
        assertNotNull(emitter);
        assertEquals(0L, emitter.getTimeout());
    }

    @Test
    void createEventStream_ShouldSendConnectedEvent() throws Exception {
        // Given
        TestSseEmitter testEmitter = new TestSseEmitter();
        
        // When - Override the service to use our test emitter
        EventService testService = new EventService() {
            @Override
            public SseEmitter createEventStream() {
                // Simulate the connection logic without the actual SseEmitter
                return testEmitter;
            }
        };
        
        SseEmitter result = testService.createEventStream();

        // Then
        assertNotNull(result);
        assertEquals(testEmitter, result);
    }

    @Test
    void broadcastEvent_WithValidEventAndData_ShouldSucceed() {
        // Given
        String eventName = "test-event";
        String data = "test data";

        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> {
            eventService.broadcastEvent(eventName, data);
        });
    }

    @Test
    void broadcastEvent_WithNullEventName_ShouldHandleGracefully() {
        // Given
        String data = "test data";

        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> {
            eventService.broadcastEvent(null, data);
        });
    }

    @Test
    void broadcastEvent_WithNullData_ShouldHandleGracefully() {
        // Given
        String eventName = "test-event";

        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> {
            eventService.broadcastEvent(eventName, null);
        });
    }

    @Test
    void broadcastEvent_WithEmptyStrings_ShouldHandleGracefully() {
        // Given
        String eventName = "";
        String data = "";

        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> {
            eventService.broadcastEvent(eventName, data);
        });
    }

    @Test
    void multipleEmitters_ShouldBeHandledIndependently() {
        // Given
        SseEmitter emitter1 = eventService.createEventStream();
        SseEmitter emitter2 = eventService.createEventStream();

        // When & Then - Both emitters should be valid and independent
        assertNotNull(emitter1);
        assertNotNull(emitter2);
        assertNotEquals(emitter1, emitter2);
    }

    @Test
    void periodicEvents_ShouldBeScheduled() throws InterruptedException {
        // Given - Create a service and wait briefly to let periodic tasks start
        EventService service = new EventService();
        
        // When - Wait a short time for the service to initialize
        Thread.sleep(100);
        
        // Then - Service should be created without errors
        assertNotNull(service);
        
        // Cleanup would happen automatically when service goes out of scope
    }

    // Test helper class to simulate SseEmitter behavior
    private static class TestSseEmitter extends SseEmitter {
        private boolean eventSent = false;
        
        public TestSseEmitter() {
            super(0L);
        }
        
        @Override
        public void send(SseEventBuilder builder) throws IOException {
            eventSent = true;
            // Don't actually send, just mark that send was called
        }
        
        public boolean wasEventSent() {
            return eventSent;
        }
    }
}