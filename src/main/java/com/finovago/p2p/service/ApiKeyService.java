package com.finovago.p2p.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finovago.p2p.dto.ApiKeyResponse;
import com.finovago.p2p.dto.ApiKeyStatusResponse;
import com.finovago.p2p.model.ApiKey;
import com.finovago.p2p.model.Merchant;
import com.finovago.p2p.repository.ApiKeyRepository;

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

    private final ApiKeyRepository apiKeyRepository;
    private final PasswordEncoder passwordEncoder;

    public ApiKeyService(ApiKeyRepository apiKeyRepository, PasswordEncoder passwordEncoder) {
        this.apiKeyRepository = apiKeyRepository;
        this.passwordEncoder = passwordEncoder;
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
            apiKey.rotate(keyPrefix, hashedSecret);
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

        Optional<ApiKey> apiKey = apiKeyRepository.findByKeyPrefix(keyPrefix)
                .filter(ApiKey::isActive)
                .filter(key -> passwordEncoder.matches(secret, key.getHashedSecret()));

        apiKey.ifPresent(key -> {
            key.setLastUsedAt(LocalDateTime.now());
            apiKeyRepository.save(key);
        });

        return apiKey;
    }

    private String randomUrlSafe(int numBytes) {
        byte[] randomBytes = new byte[numBytes];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
