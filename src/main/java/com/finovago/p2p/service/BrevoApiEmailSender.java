package com.finovago.p2p.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

/**
 * Brevo's transactional email HTTP API (port 443), used only in prod. Render (like most PaaS
 * hosts) blocks outbound SMTP ports at the network level to fight spam, so the SMTP relay that
 * works fine in dev/tests times out there - the HTTP API is Brevo's documented workaround. Uses a
 * separate API key from the SMTP one (Brevo dashboard: Settings -> SMTP & API -> API Keys).
 */
@Slf4j
@Service
@Profile("prod")
public class BrevoApiEmailSender implements EmailSender {

    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

    private final RestClient restClient;
    private final String apiKey;
    private final String fromAddress;

    public BrevoApiEmailSender(
            @Value("${app.brevo.api-key}") String apiKey,
            @Value("${app.mail.from}") String fromAddress) {
        // Built directly rather than injecting RestClient.Builder: that bean isn't autoconfigured
        // in this Spring Boot 4.1.0 setup, and this is the only caller - no need for a shared bean.
        this.restClient = RestClient.create();
        this.apiKey = apiKey;
        this.fromAddress = fromAddress;
    }

    @Override
    public void send(String to, String subject, String body) {
        Map<String, Object> payload = Map.of(
                "sender", Map.of("email", fromAddress),
                "to", List.of(Map.of("email", to)),
                "subject", subject,
                "textContent", body);

        restClient.post()
                .uri(BREVO_API_URL)
                .header("api-key", apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .body(payload)
                .retrieve()
                .toBodilessEntity();

        log.debug("Email sent via Brevo API to {} with subject: {}", to, subject);
    }
}
