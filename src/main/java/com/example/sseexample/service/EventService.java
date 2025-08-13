package com.example.sseexample.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class EventService {

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public EventService() {
        startPeriodicEvents();
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
        String timestamp = getCurrentTimestamp();
        String eventData = String.format("{\"message\":\"%s\", \"timestamp\":\"%s\"}", data, timestamp);
        
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
                "New feature deployed"
            };
            String randomMessage = sampleMessages[(int) (Math.random() * sampleMessages.length)];
            broadcastEvent("notification", randomMessage);
        }, 10, 15, TimeUnit.SECONDS);
    }

    private String getCurrentTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}