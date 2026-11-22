package com.example.sseexample.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sends the root path to the static test client. Nothing was mapped at "/", so
 * opening the service URL showed the whitelabel 404 page.
 */
@Configuration
public class RootRedirectConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", "/test.html");
    }
}
