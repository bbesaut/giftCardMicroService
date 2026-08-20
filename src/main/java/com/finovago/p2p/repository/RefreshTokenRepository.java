package com.finovago.p2p.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.finovago.p2p.model.RefreshToken;
import com.finovago.p2p.model.User;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByExpiryDateBefore(Instant cutoff);

    List<RefreshToken> findByUserAndRevokedFalse(User user);
}
