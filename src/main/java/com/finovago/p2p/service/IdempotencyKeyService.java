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

import com.finovago.p2p.dto.RedemptionResponse;
import com.finovago.p2p.exception.IdempotencyKeyConflictException;
import com.finovago.p2p.exception.IdempotencyKeyInProgressException;
import com.finovago.p2p.model.IdempotencyKey;
import com.finovago.p2p.repository.IdempotencyKeyRepository;

/**
 * Each method here runs in its own transaction (REQUIRES_NEW), independent of the caller's
 * business transaction. This lets claim() act as a fast, immediately-committed lock that
 * concurrent duplicate requests observe right away, and lets complete()/discard() record the
 * outcome after the business transaction has already committed or rolled back.
 */
@Service
public class IdempotencyKeyService {
    private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyService.class);

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final long ttlHours;

    public IdempotencyKeyService(
            IdempotencyKeyRepository idempotencyKeyRepository,
            @Value("${app.idempotency.ttl-hours:24}") long ttlHours) {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.ttlHours = ttlHours;
    }

    /**
     * Attempts to claim the key for a new request. Returns the cached response if this exact
     * request already completed successfully under this key. Returns empty if the caller should
     * proceed with the business logic. Throws if the key is already in use by an in-flight or
     * mismatched request.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<RedemptionResponse> claim(Long merchantId, String idempotencyKey, String requestHash) {
        Optional<IdempotencyKey> existing = idempotencyKeyRepository.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey);
        if (existing.isPresent()) {
            return resolveExisting(existing.get(), requestHash);
        }

        try {
            IdempotencyKey claimRow = new IdempotencyKey(merchantId, idempotencyKey, requestHash, LocalDateTime.now().plusHours(ttlHours));
            idempotencyKeyRepository.saveAndFlush(claimRow);
            return Optional.empty();
        } catch (DataIntegrityViolationException e) {
            // Lost the race against a concurrent request with the same key: fall back to whatever it wrote.
            IdempotencyKey concurrent = idempotencyKeyRepository.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey)
                    .orElseThrow(() -> e);
            return resolveExisting(concurrent, requestHash);
        }
    }

    private Optional<RedemptionResponse> resolveExisting(IdempotencyKey existing, String requestHash) {
        if (!existing.matchesRequest(requestHash)) {
            throw new IdempotencyKeyConflictException("This Idempotency-Key was already used with a different request payload");
        }
        if (existing.isInProgress()) {
            throw new IdempotencyKeyInProgressException("A request with this Idempotency-Key is already being processed");
        }
        return Optional.of(new RedemptionResponse(
                existing.getResponseStatus(),
                existing.getDeductedAmount(),
                existing.getRemainingBalance(),
                existing.getRemainingToPay()
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long merchantId, String idempotencyKey, RedemptionResponse response) {
        idempotencyKeyRepository.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey)
                .ifPresent(key -> key.complete(response.status(), response.deductedAmount(), response.remainingBalance(), response.remainingToPay()));
    }

    /** Best-effort cleanup so a genuine business failure (e.g. inactive card) doesn't permanently block retries under the same key. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void discard(Long merchantId, String idempotencyKey) {
        try {
            idempotencyKeyRepository.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey)
                    .ifPresent(idempotencyKeyRepository::delete);
        } catch (Exception e) {
            log.warn("Failed to discard idempotency key claim for merchant {}: {}", merchantId, e.getMessage());
        }
    }

    public String hashRequest(String giftCardCode, double amount) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((giftCardCode + '|' + amount).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    @Transactional
    public void deleteExpired(LocalDateTime cutoff) {
        List<IdempotencyKey> expired = idempotencyKeyRepository.findByExpiresAtBefore(cutoff);
        idempotencyKeyRepository.deleteAll(expired);
    }
}
