package com.finovago.p2p.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.finovago.p2p.exception.InvalidRefreshTokenException;
import com.finovago.p2p.model.RefreshToken;
import com.finovago.p2p.model.User;
import com.finovago.p2p.repository.RefreshTokenRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final long refreshTokenExpirationMs;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${application.security.jwt.refresh-token-expiration}") long refreshTokenExpirationMs) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    public String createRefreshToken(User user) {
        String rawToken = UUID.randomUUID().toString();
        Instant expiryDate = Instant.now().plusMillis(refreshTokenExpirationMs);

        refreshTokenRepository.save(new RefreshToken(hash(rawToken), user, expiryDate));

        log.debug("Refresh token created for user: {} (expires: {})", user.getEmail(), expiryDate);

        return rawToken;
    }

    public User validateAndRotate(String rawToken) {
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> {
                    log.warn("Token rotation failed - token hash not found (possible invalid token)");
                    return new InvalidRefreshTokenException("Refresh token not found");
                });

        if (existing.isRevoked()) {
            log.warn("Token rotation failed - token already revoked (possible replay attack)");
            throw new InvalidRefreshTokenException("Refresh token has been revoked");
        }
        if (existing.isExpired()) {
            log.warn("Token rotation failed - token expired");
            throw new InvalidRefreshTokenException("Refresh token has expired");
        }

        existing.revoke();
        refreshTokenRepository.save(existing);

        log.info("Token rotation completed for user: {}", existing.getUser().getEmail());

        return existing.getUser();
    }

    public void revoke(String rawToken) {
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> {
                    log.warn("Logout failed - refresh token not found or invalid");
                    return new InvalidRefreshTokenException("Refresh token not found");
                });

        existing.revoke();
        refreshTokenRepository.save(existing);

        log.debug("Refresh token revoked for user: {}", existing.getUser().getEmail());
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
