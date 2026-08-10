package com.example.sseexample.controller;

import com.example.sseexample.service.EventService;
import com.example.sseexample.service.SseCapacityExceededException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents() {
        return eventService.createEventStream();
    }

    @PostMapping("/trigger-event")
    public ResponseEntity<String> triggerEvent(@RequestBody String message) {
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