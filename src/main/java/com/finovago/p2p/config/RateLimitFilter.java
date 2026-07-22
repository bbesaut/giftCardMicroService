package com.finovago.p2p.config;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

import jakarta.annotation.PreDestroy;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Caps requests per client IP on endpoints exposed to enumeration/brute-force abuse
 * (login credential stuffing, gift card code guessing on lookup/redeem). Limits are
 * per-instance only (in-memory buckets) - if this service is ever scaled horizontally,
 * this must move to a shared store (e.g. Redis) or each instance will allow the full
 * quota independently.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<String> PROTECTED_PATH_PREFIXES = List.of(
        "/api/v1/auth/login",
        "/api/v1/giftcards/lookup/",
        "/api/v1/giftcards/redeem"
    );

    private final boolean enabled;
    private final int capacity;
    private final Duration refillPeriod;
    private final Map<String, Bucket> bucketsByKey = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor();

    public RateLimitFilter(
            @Value("${app.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.rate-limit.capacity:10}") int capacity,
            @Value("${app.rate-limit.refill-period-seconds:60}") long refillPeriodSeconds) {
        this.enabled = enabled;
        this.capacity = capacity;
        this.refillPeriod = Duration.ofSeconds(refillPeriodSeconds);
        cleanupExecutor.scheduleAtFixedRate(this::evictIdleBuckets, 10, 10, TimeUnit.MINUTES);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || matchedPrefix(request.getRequestURI()) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String matchedPrefix = matchedPrefix(request.getRequestURI());
        String clientIp = resolveClientIp(request);
        String bucketKey = matchedPrefix + "|" + clientIp;

        Bucket bucket = bucketsByKey.computeIfAbsent(bucketKey, key -> newBucket());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (!probe.isConsumed()) {
            long retryAfterSeconds = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000);
            log.warn("Rate limit exceeded on {} for IP: {}", matchedPrefix, clientIp);

            response.setStatus(429);
            response.setContentType("application/json");
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));

            Map<String, Object> errorResponse = Map.of(
                "error", "Too Many Requests",
                "message", "Too many requests. Please try again later."
            );
            response.getWriter().write(MAPPER.writeValueAsString(errorResponse));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String matchedPrefix(String uri) {
        return PROTECTED_PATH_PREFIXES.stream()
                .filter(uri::startsWith)
                .findFirst()
                .orElse(null);
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.builder().capacity(capacity).refillGreedy(capacity, refillPeriod).build();
        return Bucket.builder().addLimit(limit).build();
    }

    private void evictIdleBuckets() {
        bucketsByKey.entrySet().removeIf(entry -> entry.getValue().getAvailableTokens() >= capacity);
    }

    private String resolveClientIp(HttpServletRequest request) {
        // Not reading X-Forwarded-For here: without a trusted reverse proxy stripping/overwriting
        // it first, a client can set it themselves and defeat the whole rate limit.
        return request.getRemoteAddr();
    }

    @PreDestroy
    public void shutdown() {
        cleanupExecutor.shutdown();
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }
}
