package com.finovago.p2p.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.finovago.p2p.model.PasswordResetToken;
import com.finovago.p2p.model.User;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    List<PasswordResetToken> findByExpiryDateBefore(Instant cutoff);

    List<PasswordResetToken> findByUserAndUsedFalse(User user);
}
