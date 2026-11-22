package com.example.sseexample.config;

import com.example.sseexample.SseExampleApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.HttpURLConnection;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The root path used to return the whitelabel 404, which is the first thing a
 * visitor sees. It must send them to the test client instead.
 */
@SpringBootTest(
    classes = SseExampleApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class RootRedirectTest {

    @LocalServerPort
    private int port;

    @Test
    void root_ShouldRedirectToTestClient() throws Exception {
        HttpURLConnection connection =
            (HttpURLConnection) URI.create("http://localhost:" + port + "/").toURL().openConnection();
        // Inspect the redirect itself rather than the page it leads to.
        connection.setInstanceFollowRedirects(false);
        try {
            assertEquals(302, connection.getResponseCode());
            // Relative, so it stays correct behind a proxy or ingress that rewrites the host.
            assertEquals("/test.html", connection.getHeaderField("Location"));
        } finally {
            connection.disconnect();
        }
    }

    @Test
    void redirectTarget_ShouldExist() throws Exception {
        HttpURLConnection connection =
            (HttpURLConnection) URI.create("http://localhost:" + port + "/test.html").toURL().openConnection();
        try {
            assertEquals(200, connection.getResponseCode());
        } finally {
            connection.disconnect();
        }
    }
}
