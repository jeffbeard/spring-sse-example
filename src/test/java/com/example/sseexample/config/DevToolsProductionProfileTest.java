package com.example.sseexample.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Devtools must never be live in the production profile (issue #17). The image no
 * longer ships the jar; these properties are the second line of defence for any
 * environment where it lands on the classpath anyway.
 */
@SpringBootTest
@ActiveProfiles("production")
class DevToolsProductionProfileTest {

    @Autowired
    private Environment environment;

    @Test
    void productionProfile_DisablesDevtoolsRestart() {
        assertEquals("false", environment.getProperty("spring.devtools.restart.enabled"));
    }

    @Test
    void productionProfile_DisablesLiveReload() {
        assertEquals("false", environment.getProperty("spring.devtools.livereload.enabled"));
    }
}
