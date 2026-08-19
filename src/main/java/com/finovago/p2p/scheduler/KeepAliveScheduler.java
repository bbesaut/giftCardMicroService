package com.finovago.p2p.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * Render's free tier spins a web service down after 15 minutes without an inbound HTTP request on
 * its public port. Pinging our own health endpoint from inside the instance still counts as that
 * traffic, so this keeps the service warm without needing an external uptime monitor.
 * {@code RENDER_EXTERNAL_URL} is set automatically by Render for every web service - no manual
 * configuration needed there. A no-op if that URL isn't set (e.g. running outside Render).
 */
@Component
@ConditionalOnProperty(name = "app.keep-alive.enabled", havingValue = "true")
public class KeepAliveScheduler {
    private static final Logger log = LoggerFactory.getLogger(KeepAliveScheduler.class);

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.keep-alive.url:${RENDER_EXTERNAL_URL:}}")
    private String keepAliveUrl;

    @Scheduled(fixedDelayString = "${app.keep-alive.interval-ms:720000}")
    public void ping() {
        if (!StringUtils.hasText(keepAliveUrl)) {
            log.warn("Keep-alive is enabled but no URL is configured (app.keep-alive.url / RENDER_EXTERNAL_URL) - skipping");
            return;
        }
        try {
            restTemplate.getForEntity(keepAliveUrl, String.class);
            log.debug("Keep-alive ping sent to {}", keepAliveUrl);
        } catch (Exception e) {
            log.warn("Keep-alive ping to {} failed: {}", keepAliveUrl, e.getMessage());
        }
    }
}
