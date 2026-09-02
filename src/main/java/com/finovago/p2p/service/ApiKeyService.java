package com.finovago.p2p.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.finovago.p2p.dto.ApiKeyResponse;
import com.finovago.p2p.dto.ApiKeyStatusResponse;
import com.finovago.p2p.model.ApiKey;
import com.finovago.p2p.model.Merchant;
import com.finovago.p2p.repository.ApiKeyRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import lombok.extern.slf4j.Slf4j;

/**
 * Owns the api_keys table: generation, rotation, revocation, and presented-key validation. A key
 * belongs directly to a Merchant - it is its own identity, not a stand-in "service account" User
 * with a fake email/password.
 */
@Slf4j
@Service
public class ApiKeyService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String PREFIX_HEADER = "fovak_";
    private static final long MAX_CACHE_ENTRIES = 10_000;

    private final ApiKeyRepository apiKeyRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * keyPrefix -> ApiKey, so resolve() can skip the DB lookup on repeat calls with the same key.
     * BCrypt verification still runs on every call against the cached hash - only the SELECT is
     * skipped. TTL is a backstop; rotate()/revoke() invalidate explicitly so a change takes effect
     * immediately rather than waiting out the window.
     */
    private final Cache<String, ApiKey> keyPrefixCache;

    public ApiKeyService(
            ApiKeyRepository apiKeyRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.api-key-cache.ttl-minutes:2}") long cacheTtlMinutes) {
        this.apiKeyRepository = apiKeyRepository;
        this.passwordEncoder = passwordEncoder;
        this.keyPrefixCache = Caffeine.newBuilder()
                .expireAfterWrite(cacheTtlMinutes, TimeUnit.MINUTES)
                .maximumSize(MAX_CACHE_ENTRIES)
                .build();
    }

    /** Creates the merchant's first key, or rotates it if one already exists - either way the old secret stops working. */
    @Transactional
    public ApiKeyResponse generateOrRotate(Merchant merchant) {
        String keyPrefix = PREFIX_HEADER + randomUrlSafe(9);
        String secret = randomUrlSafe(32);
        String hashedSecret = passwordEncoder.encode(secret);

        ApiKey apiKey = apiKeyRepository.findByMerchant_Id(merchant.getId()).orElse(null);
        boolean isNew = apiKey == null;

        if (isNew) {
            apiKey = new ApiKey(merchant, keyPrefix, hashedSecret);
        } else {
            String previousPrefix = apiKey.getKeyPrefix();
            apiKey.rotate(keyPrefix, hashedSecret);
            invalidateCacheAfterCommit(previousPrefix);
        }
        apiKeyRepository.save(apiKey);

        log.info("API key {} for merchantId: {}", isNew ? "generated" : "rotated", merchant.getId());

        return new ApiKeyResponse(keyPrefix, keyPrefix + "." + secret);
    }

    @Transactional
    public ApiKeyStatusResponse revoke(Merchant merchant) {
        Optional<ApiKey> apiKey = apiKeyRepository.findByMerchant_Id(merchant.getId());
        if (apiKey.isEmpty()) {
            return new ApiKeyStatusResponse(null, false);
        }

        ApiKey key = apiKey.get();
        key.setActive(false);
        key.setRevokedAt(LocalDateTime.now());
        apiKeyRepository.save(key);
        invalidateCacheAfterCommit(key.getKeyPrefix());

        log.info("API key {} revoked for merchantId: {}", key.getKeyPrefix(), merchant.getId());
        return new ApiKeyStatusResponse(key.getKeyPrefix(), false);
    }

    /** Validates a presented "{prefix}.{secret}" key and returns the ApiKey it matches, if valid and active. */
    @Transactional
    public Optional<ApiKey> resolve(String presentedKey) {
        int separator = presentedKey.indexOf('.');
        if (separator <= 0 || separator == presentedKey.length() - 1) {
            return Optional.empty();
        }

        String keyPrefix = presentedKey.substring(0, separator);
        String secret = presentedKey.substring(separator + 1);

        ApiKey cachedKey = keyPrefixCache.get(keyPrefix, prefix -> apiKeyRepository.findByKeyPrefix(prefix).orElse(null));

        Optional<ApiKey> apiKey = Optional.ofNullable(cachedKey)
                .filter(ApiKey::isActive)
                .filter(key -> passwordEncoder.matches(secret, key.getHashedSecret()));

        apiKey.ifPresent(key -> apiKeyRepository.updateLastUsedAt(key.getId(), LocalDateTime.now()));

        return apiKey;
    }

    /**
     * Invalidating immediately (mid-transaction) would let a concurrent resolve() re-cache the
     * pre-change row before this transaction's UPDATE is even committed, leaving the cache stuck
     * on stale data for the rest of the TTL. Deferring invalidation to after commit closes that
     * window: nothing can re-cache the old row once it's actually gone from the DB. Falls back to
     * immediate invalidation outside a transaction (e.g. unit tests calling the service directly).
     */
    private void invalidateCacheAfterCommit(String keyPrefix) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            keyPrefixCache.invalidate(keyPrefix);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                keyPrefixCache.invalidate(keyPrefix);
            }
        });
    }

    private String randomUrlSafe(int numBytes) {
        byte[] randomBytes = new byte[numBytes];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
