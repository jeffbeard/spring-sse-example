package com.example.sseexample.controller;

import com.example.sseexample.service.EventService;
import com.example.sseexample.service.SseCapacityExceededException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api")
public class EventController {

    static final int MAX_MESSAGE_LENGTH = 1000;

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents() {
        return eventService.createEventStream();
    }

    /**
     * Broadcasts a custom event. The body is a JSON string, for example
     * {@code "Hello"}. It is bound as a {@link JsonNode} so that Jackson decodes it:
     * binding to {@code String} hands over the raw body, quotes included, which was
     * then JSON-encoded a second time on the way out (issue #65).
     */
    @PostMapping(value = "/trigger-event", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> triggerEvent(@RequestBody JsonNode body) {
        if (!body.isString()) {
            return ResponseEntity.badRequest().body("Request body must be a JSON string");
        }
        String message = body.stringValue();
        if (message.isBlank()) {
            return ResponseEntity.badRequest().body("Message must not be blank");
        }
        // One request fans out to every connection, so the size is bounded here.
        if (message.length() > MAX_MESSAGE_LENGTH) {
            return ResponseEntity.badRequest()
                .body("Message must be at most " + MAX_MESSAGE_LENGTH + " characters");
        }
        eventService.broadcastEvent("custom", message);
        return ResponseEntity.ok("Event triggered");
    }

    @GetMapping("/")
    public ResponseEntity<String> home() {
        return ResponseEntity.ok("SSE Example Server is running! Visit /test.html to see SSE in action.");
    }

    /**
     * Refuses new streams once the connection cap is reached, telling well-behaved
     * clients when to come back instead of dropping them silently.
     */
    @ExceptionHandler(SseCapacityExceededException.class)
    public ResponseEntity<String> handleCapacityExceeded(SseCapacityExceededException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header(HttpHeaders.RETRY_AFTER, "5")
            .body(e.getMessage());
    }
}