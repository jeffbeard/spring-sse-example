package com.example.sseexample;

import com.example.sseexample.controller.EventController;
import com.example.sseexample.service.EventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.test.context.ActiveProfiles("test")
class SseExampleApplicationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private EventController eventController;

    @Autowired
    private EventService eventService;

    @Test
    void contextLoads() {
        assertNotNull(eventController);
        assertNotNull(eventService);
    }

    @Test
    void homeEndpoint_ShouldReturnWelcomeMessage() {
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/", String.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("SSE Example Server is running! Visit /test.html to see SSE in action.",
                response.getBody());
    }

    @Test
    void triggerEventEndpoint_ShouldReturnSuccessMessage() {
        // Given
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        HttpEntity<String> request = new HttpEntity<>("\"Test message\"", headers);

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/trigger-event", request, String.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Event triggered", response.getBody());
    }

    @Test
    void triggerEventEndpoint_WithEmptyMessage_ShouldSucceed() {
        // Given
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        HttpEntity<String> request = new HttpEntity<>("\"\"", headers);

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/trigger-event", request, String.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Event triggered", response.getBody());
    }

    @Test
    void sseEndpoint_ShouldReturnEventStreamContentType() {
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/events", String.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getHeaders().get("Content-Type").get(0)
                .contains("text/event-stream"));
    }

    @Test
    void staticContent_ShouldBeAccessible() {
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/test.html", String.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("SSE Example"));
    }
}