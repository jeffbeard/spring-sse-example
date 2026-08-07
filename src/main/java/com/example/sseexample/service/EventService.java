package com.example.sseexample.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class EventService {

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EventService() {
        this(true);
    }
    
    public EventService(boolean enablePeriodicEvents) {
        if (enablePeriodicEvents) {
            startPeriodicEvents();
        }
    }

    public SseEmitter createEventStream() {
        SseEmitter emitter = new SseEmitter(0L);
        
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError((e) -> emitters.remove(emitter));
        
        emitters.add(emitter);
        
        try {
            emitter.send(SseEmitter.event()
                .name("connected")
                .data("Connected to SSE stream at " + getCurrentTimestamp()));
        } catch (IOException e) {
            emitters.remove(emitter);
        }
        
        return emitter;
    }

    public void broadcastEvent(String eventName, String data) {
        String eventData = buildPayload(data);

        emitters.removeIf(emitter -> {
            try {
                emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(eventData));
                return false;
            } catch (IOException e) {
                return true;
            }
        });
    }

    private void startPeriodicEvents() {
        scheduler.scheduleAtFixedRate(() -> {
            broadcastEvent("heartbeat", "Server heartbeat");
        }, 0, 30, TimeUnit.SECONDS);

        scheduler.scheduleAtFixedRate(() -> {
            String[] sampleMessages = {
                "New user registered",
                "Order processed successfully", 
                "System maintenance scheduled",
                "Database backup completed",
                "New feature deployed",
                "Melba told me a secret"
            };
            String randomMessage = sampleMessages[(int) (Math.random() * sampleMessages.length)];
            broadcastEvent("notification", randomMessage);
        }, 10, 15, TimeUnit.SECONDS);
    }

    String buildPayload(String message) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("message", message);
        payload.put("timestamp", getCurrentTimestamp());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            // String values cannot fail serialization; fall back defensively.
            return "{\"message\":null,\"timestamp\":null}";
        }
    }

    private String getCurrentTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}