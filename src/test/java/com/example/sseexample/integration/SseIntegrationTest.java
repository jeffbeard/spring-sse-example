package com.example.sseexample.integration;

import com.example.sseexample.SseExampleApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
    classes = SseExampleApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class SseIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void triggerEvent_ShouldReturnSuccessResponse() {
        // Given
        String message = "Integration test message";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>("\"" + message + "\"", headers);

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
            "http://localhost:" + port + "/api/trigger-event",
            request,
            String.class
        );

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Event triggered", response.getBody());
    }

    @Test
    void sseEndpoint_ShouldDeliverEvents() throws Exception {
        // This test simulates a client connecting to the SSE stream
        // and receiving events
        
        // We'll use a separate thread to connect to the SSE endpoint
        // and a CountDownLatch to wait for events
        
        final CountDownLatch connectionLatch = new CountDownLatch(1);
        final CountDownLatch eventLatch = new CountDownLatch(1);
        final AtomicBoolean receivedConnectedEvent = new AtomicBoolean(false);
        final AtomicInteger eventCount = new AtomicInteger(0);
        
        // Start a thread to connect to the SSE endpoint
        Thread sseClientThread = new Thread(() -> {
            try {
                URL url = new URL("http://localhost:" + port + "/api/events");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("Accept", "text/event-stream");
                connection.setDoInput(true);
                
                // Signal that we've connected
                connectionLatch.countDown();
                
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("[DEBUG_LOG] SSE received: " + line);
                        
                        // Check for the connected event
                        if (line.contains("event:connected")) {
                            receivedConnectedEvent.set(true);
                        }
                        
                        // Count all events
                        if (line.startsWith("event:")) {
                            eventCount.incrementAndGet();
                            eventLatch.countDown();
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        
        // Start the client thread
        sseClientThread.start();
        
        // Wait for the connection to be established
        assertTrue(connectionLatch.await(5, TimeUnit.SECONDS), "Failed to connect to SSE endpoint");
        
        // Trigger an event
        triggerEvent_ShouldReturnSuccessResponse();
        
        // Wait for at least one event to be received
        assertTrue(eventLatch.await(5, TimeUnit.SECONDS), "No events received");
        
        // Verify that we received the connected event
        assertTrue(receivedConnectedEvent.get(), "Did not receive connected event");
        
        // Verify that we received at least one event
        assertTrue(eventCount.get() > 0, "No events received");
        
        // Clean up
        sseClientThread.interrupt();
    }
    
    @Test
    void multipleClients_ShouldAllReceiveEvents() throws Exception {
        // This test simulates multiple clients connecting to the SSE stream
        // and all receiving the same events
        
        final int clientCount = 3;
        final CountDownLatch connectionLatch = new CountDownLatch(clientCount);
        final CountDownLatch eventLatch = new CountDownLatch(clientCount);
        final AtomicInteger connectedClients = new AtomicInteger(0);
        final AtomicInteger clientsReceivingEvents = new AtomicInteger(0);
        
        // Start multiple client threads
        for (int i = 0; i < clientCount; i++) {
            Thread clientThread = new Thread(() -> {
                try {
                    URL url = new URL("http://localhost:" + port + "/api/events");
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestProperty("Accept", "text/event-stream");
                    connection.setDoInput(true);
                    
                    // Signal that we've connected
                    connectedClients.incrementAndGet();
                    connectionLatch.countDown();
                    
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                        String line;
                        boolean receivedEvent = false;
                        
                        while ((line = reader.readLine()) != null) {
                            System.out.println("[DEBUG_LOG] Client thread " + Thread.currentThread().getId() + " received: " + line);
                            
                            // Check for any event after the custom event is triggered
                            if (line.contains("event:custom")) {
                                receivedEvent = true;
                                clientsReceivingEvents.incrementAndGet();
                                eventLatch.countDown();
                                break; // We can exit after receiving the custom event
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            
            clientThread.start();
        }
        
        // Wait for all clients to connect
        assertTrue(connectionLatch.await(5, TimeUnit.SECONDS), "Not all clients connected");
        assertEquals(clientCount, connectedClients.get(), "Not all clients connected");
        
        // Wait a bit to ensure all clients are ready to receive events
        Thread.sleep(1000);
        
        // Trigger a custom event
        triggerEvent_ShouldReturnSuccessResponse();
        
        // Wait for all clients to receive the event
        assertTrue(eventLatch.await(5, TimeUnit.SECONDS), "Not all clients received the event");
        assertEquals(clientCount, clientsReceivingEvents.get(), "Not all clients received the event");
    }
}