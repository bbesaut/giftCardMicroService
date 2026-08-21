package com.finovago.p2p.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.finovago.p2p.exception.IdempotencyKeyConflictException;
import com.finovago.p2p.exception.IdempotencyKeyInProgressException;
import com.finovago.p2p.model.IdempotencyKey;
import com.finovago.p2p.repository.IdempotencyKeyRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Endpoint-agnostic idempotency support: any mutating endpoint can claim a merchant-scoped key
 * before running its business logic, and cache its response (serialized as JSON) for replay on
 * retry. Each method here runs in its own transaction (REQUIRES_NEW), independent of the
 * caller's business transaction - claim() acts as a fast, immediately-committed lock that
 * concurrent duplicate requests observe right away.
 */
@Service
public class IdempotencyKeyService {
    private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyService.class);

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final JsonMapper jsonMapper;
    private final long ttlHours;

    public IdempotencyKeyService(
            IdempotencyKeyRepository idempotencyKeyRepository,
            JsonMapper jsonMapper,
            @Value("${app.idempotency.ttl-hours:24}") long ttlHours) {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.jsonMapper = jsonMapper;
        this.ttlHours = ttlHours;
    }

    /**
     * Attempts to claim the key for a new request. Returns the cached response if this exact
     * request already completed successfully under this key. Returns empty if the caller should
     * proceed with the business logic. Throws if the key is already in use by an in-flight or
     * mismatched request.
     *
     * Scoped by (merchant, endpoint, key) - like Stripe/PayPal/AWS - not just (merchant, key), so
     * a client reusing the same key value on two different endpoints can never have one silently
     * replay the other's response; requestHash only needs to catch reuse *within* the same
     * endpoint with a different payload.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T> Optional<T> claim(Long merchantId, String endpoint, String idempotencyKey, String requestHash, Class<T> responseType) {
        Optional<IdempotencyKey> existing = idempotencyKeyRepository.findByMerchantIdAndEndpointAndIdempotencyKey(merchantId, endpoint, idempotencyKey);
        if (existing.isPresent()) {
            return resolveExisting(existing.get(), requestHash, responseType);
        }

        try {
            IdempotencyKey claimRow = new IdempotencyKey(merchantId, idempotencyKey, endpoint, requestHash, LocalDateTime.now().plusHours(ttlHours));
            idempotencyKeyRepository.saveAndFlush(claimRow);
            return Optional.empty();
        } catch (DataIntegrityViolationException e) {
            // Lost the race against a concurrent request with the same key: fall back to whatever it wrote.
            IdempotencyKey concurrent = idempotencyKeyRepository.findByMerchantIdAndEndpointAndIdempotencyKey(merchantId, endpoint, idempotencyKey)
                    .orElseThrow(() -> e);
            return resolveExisting(concurrent, requestHash, responseType);
        }
    }

    private <T> Optional<T> resolveExisting(IdempotencyKey existing, String requestHash, Class<T> responseType) {
        if (!existing.matchesRequest(requestHash)) {
            throw new IdempotencyKeyConflictException("This Idempotency-Key was already used with a different request payload");
        }
        if (existing.isInProgress()) {
            throw new IdempotencyKeyInProgressException("A request with this Idempotency-Key is already being processed");
        }
        return Optional.of(deserialize(existing.getResponseBody(), responseType));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long merchantId, String endpoint, String idempotencyKey, Object response) {
        idempotencyKeyRepository.findByMerchantIdAndEndpointAndIdempotencyKey(merchantId, endpoint, idempotencyKey)
                .ifPresent(key -> key.complete(serialize(response)));
    }

    /** Best-effort cleanup so a genuine business failure (e.g. inactive card) doesn't permanently block retries under the same key. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void discard(Long merchantId, String endpoint, String idempotencyKey) {
        try {
            idempotencyKeyRepository.findByMerchantIdAndEndpointAndIdempotencyKey(merchantId, endpoint, idempotencyKey)
                    .ifPresent(idempotencyKeyRepository::delete);
        } catch (Exception e) {
            log.warn("Failed to discard idempotency key claim for merchant {}: {}", merchantId, e.getMessage());
        }
    }

    public String hashRequest(String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(String.join("|", parts).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    @Transactional
    public int deleteExpired(LocalDateTime cutoff) {
        List<IdempotencyKey> expired = idempotencyKeyRepository.findByExpiresAtBefore(cutoff);
        idempotencyKeyRepository.deleteAll(expired);
        return expired.size();
    }

    private String serialize(Object response) {
        try {
            return jsonMapper.writeValueAsString(response);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize idempotent response", e);
        }
    }

    private <T> T deserialize(String responseBody, Class<T> responseType) {
        try {
            return jsonMapper.readValue(responseBody, responseType);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to deserialize cached idempotent response", e);
        }
    }
}
