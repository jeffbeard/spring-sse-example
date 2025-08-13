package com.example.sseexample.config;

import com.example.sseexample.service.EventService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("test")
public class TestConfig {

    @Bean
    @Primary
    public EventService eventService() {
        return new EventService(false); // Disable periodic events for testing
    }
}