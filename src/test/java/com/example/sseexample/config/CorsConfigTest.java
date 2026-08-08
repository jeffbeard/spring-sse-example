package com.example.sseexample.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.cors.allowed-origins=https://allowed.example.com")
class CorsConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void preflight_fromConfiguredOrigin_echoesThatOrigin() throws Exception {
        mockMvc.perform(options("/api/trigger-event")
                .header("Origin", "https://allowed.example.com")
                .header("Access-Control-Request-Method", "POST"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "https://allowed.example.com"));
    }

    @Test
    void preflight_fromUnlistedOrigin_isRejected() throws Exception {
        mockMvc.perform(options("/api/trigger-event")
                .header("Origin", "https://evil.example.com")
                .header("Access-Control-Request-Method", "POST"))
            .andExpect(status().isForbidden());
    }
}
