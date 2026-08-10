package com.example.sseexample;

import com.example.sseexample.config.SseProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(SseProperties.class)
public class SseExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(SseExampleApplication.class, args);
    }
}