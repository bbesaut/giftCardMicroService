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
import com.finovago.p2p.model.Merchant;
import com.finovago.p2p.repository.MerchantRepository;
import com.finovago.p2p.security.CurrentUserContext;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

import jakarta.annotation.PreDestroy;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Caps requests on endpoints exposed to enumeration/brute-force abuse. Login (not yet
 * authenticated) is capped per client IP - the only identity available pre-auth, which is what
 * credential stuffing needs limiting on. Lookup/redeem/reserve are capped per merchant (from the
 * JWT) instead of per IP: these are B2B endpoints called from a merchant's own backend, so every
 * one of a merchant's end users shares that backend's IP - an IP-based limit would throttle
 * legitimate concurrent traffic rather than abuse. Limits are per-instance only (in-memory
 * buckets) - if this service is ever scaled horizontally, this must move to a shared store (e.g.
 * Redis) or each instance will allow the full quota independently.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String LOGIN_PREFIX = "/api/v1/auth/login";

    private static final List<String> PROTECTED_PATH_PREFIXES = List.of(
        LOGIN_PREFIX,
        "/api/v1/giftcards/lookup/",
        "/api/v1/giftcards/redeem",
        "/api/v1/giftcards/reserve"
    );

    private final boolean enabled;
    private final int loginCapacity;
    private final int defaultMerchantCapacity;
    private final Duration refillPeriod;
    private final CurrentUserContext currentUserContext;
    private final MerchantRepository merchantRepository;
    private final Map<String, BucketEntry> bucketsByKey = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor();

    private record BucketEntry(Bucket bucket, int capacity) {}

    public RateLimitFilter(
            @Value("${app.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.rate-limit.login-capacity:10}") int loginCapacity,
            @Value("${app.rate-limit.merchant-capacity:300}") int defaultMerchantCapacity,
            @Value("${app.rate-limit.refill-period-seconds:60}") long refillPeriodSeconds,
            CurrentUserContext currentUserContext,
            MerchantRepository merchantRepository) {
        this.enabled = enabled;
        this.loginCapacity = loginCapacity;
        this.defaultMerchantCapacity = defaultMerchantCapacity;
        this.refillPeriod = Duration.ofSeconds(refillPeriodSeconds);
        this.currentUserContext = currentUserContext;
        this.merchantRepository = merchantRepository;
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
        boolean isLogin = LOGIN_PREFIX.equals(matchedPrefix);

        String bucketKey;
        int capacityForKey;
        String identityForLog;

        if (isLogin) {
            String clientIp = resolveClientIp(request);
            bucketKey = matchedPrefix + "|ip:" + clientIp;
            capacityForKey = loginCapacity;
            identityForLog = "IP " + clientIp;
        } else {
            Long merchantId = currentUserContext.currentMerchantId();
            bucketKey = matchedPrefix + "|merchant:" + merchantId;
            capacityForKey = capacityForMerchant(merchantId);
            identityForLog = "merchant " + merchantId;
        }

        BucketEntry entry = bucketsByKey.computeIfAbsent(bucketKey, key -> newBucketEntry(capacityForKey));
        ConsumptionProbe probe = entry.bucket().tryConsumeAndReturnRemaining(1);

        if (!probe.isConsumed()) {
            long retryAfterSeconds = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000);
            log.warn("Rate limit exceeded on {} for {}", matchedPrefix, identityForLog);

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

    private int capacityForMerchant(Long merchantId) {
        return merchantRepository.findById(merchantId)
                .map(Merchant::getRateLimitCapacity)
                .orElse(defaultMerchantCapacity);
    }

    private BucketEntry newBucketEntry(int capacity) {
        Bandwidth limit = Bandwidth.builder().capacity(capacity).refillGreedy(capacity, refillPeriod).build();
        return new BucketEntry(Bucket.builder().addLimit(limit).build(), capacity);
    }

    private void evictIdleBuckets() {
        bucketsByKey.entrySet().removeIf(entry -> entry.getValue().bucket().getAvailableTokens() >= entry.getValue().capacity());
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
