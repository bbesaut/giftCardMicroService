package com.finovago.p2p.unit;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finovago.p2p.service.BrevoApiEmailSender;
import com.sun.net.httpserver.HttpServer;

/** Runs the sender against a local HTTP server standing in for Brevo's transactional email API. */
class BrevoApiEmailSenderUnitTest {

    private static final String API_KEY = "xkeysib-test-key";
    private static final String FROM = "sender@example.com";
    private static final String PATH = "/v3/smtp/email";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<String> capturedMethod = new AtomicReference<>();
    private final AtomicReference<String> capturedPath = new AtomicReference<>();
    private final AtomicReference<String> capturedApiKey = new AtomicReference<>();
    private final AtomicReference<String> capturedContentType = new AtomicReference<>();
    private final AtomicReference<String> capturedBody = new AtomicReference<>();

    private HttpServer server;
    private BrevoApiEmailSender sender;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        sender = new BrevoApiEmailSender(baseUrl() + PATH, API_KEY, FROM);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void should_postJsonWithApiKeyAndSender_when_emailIsSent() throws IOException {
        respondWith(201, "");

        sender.send("user@example.com", "Verify your email address", "Your token is 123");

        assertEquals("POST", capturedMethod.get());
        assertEquals(PATH, capturedPath.get());
        assertEquals(API_KEY, capturedApiKey.get());
        assertEquals("application/json", capturedContentType.get());

        JsonNode json = objectMapper.readTree(capturedBody.get());
        assertEquals(FROM, json.get("sender").get("email").asText());
        assertEquals("user@example.com", json.get("to").get(0).get("email").asText());
        assertEquals("Verify your email address", json.get("subject").asText());
        assertEquals("Your token is 123", json.get("textContent").asText());
    }

    @Test
    void should_throw_when_brevoRejectsTheApiKey() throws IOException {
        respondWith(401, "{\"code\":\"unauthorized\",\"message\":\"Key not found\"}");

        assertThrows(RestClientResponseException.class,
                () -> sender.send("user@example.com", "Subject", "Body"));
    }

    @Test
    void should_throw_when_brevoFailsWithServerError() throws IOException {
        respondWith(500, "{\"code\":\"internal\"}");

        assertThrows(RestClientResponseException.class,
                () -> sender.send("user@example.com", "Subject", "Body"));
    }

    @Test
    void should_throw_when_brevoIsUnreachable() throws IOException {
        // Bind then release a port, so nothing listens on it and the connection is refused.
        int closedPort;
        try (ServerSocket probe = new ServerSocket(0)) {
            closedPort = probe.getLocalPort();
        }
        BrevoApiEmailSender unreachable = new BrevoApiEmailSender(
                "http://127.0.0.1:" + closedPort + PATH, API_KEY, FROM);

        assertThrows(ResourceAccessException.class,
                () -> unreachable.send("user@example.com", "Subject", "Body"));
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void respondWith(int status, String responseBody) {
        server.createContext(PATH, exchange -> {
            capturedMethod.set(exchange.getRequestMethod());
            capturedPath.set(exchange.getRequestURI().getPath());
            capturedApiKey.set(exchange.getRequestHeaders().getFirst("api-key"));
            capturedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            if (bytes.length == 0) {
                exchange.sendResponseHeaders(status, -1);
            } else {
                exchange.sendResponseHeaders(status, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            }
            exchange.close();
        });
    }
}
