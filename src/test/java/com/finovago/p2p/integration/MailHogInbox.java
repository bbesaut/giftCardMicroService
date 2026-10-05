package com.finovago.p2p.integration;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finovago.p2p.config.MailHogTestcontainerInitializer;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reads what the application sent through MailHog, so integration tests can pull tokens out of emails. */
final class MailHogInbox {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private MailHogInbox() {
    }

    static void clear() throws IOException, InterruptedException {
        HTTP_CLIENT.send(HttpRequest.newBuilder(URI.create(MailHogTestcontainerInitializer.httpApiBaseUrl() + "/api/v1/messages"))
                .DELETE()
                .build(), HttpResponse.BodyHandlers.discarding());
    }

    static int messageCount() throws IOException, InterruptedException {
        return messagesJson().get("total").asInt();
    }

    /** Returns the first capture group of {@code pattern} found in the most recent email body. */
    static String tokenFromLatestEmail(Pattern pattern) throws IOException, InterruptedException {
        String body = messagesJson().get("items").get(0).get("Content").get("Body").asText();
        Matcher matcher = pattern.matcher(body);
        assertTrue(matcher.find(), "No token matching " + pattern + " in email body: " + body);
        return matcher.group(1);
    }

    private static JsonNode messagesJson() throws IOException, InterruptedException {
        HttpResponse<String> response = HTTP_CLIENT.send(
                HttpRequest.newBuilder(URI.create(MailHogTestcontainerInitializer.httpApiBaseUrl() + "/api/v2/messages")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return OBJECT_MAPPER.readTree(response.body());
    }
}
