package com.finovago.p2p.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.finovago.p2p.model.EmailVerificationToken;
import com.finovago.p2p.model.User;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {
    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    List<EmailVerificationToken> findByExpiryDateBefore(Instant cutoff);

    List<EmailVerificationToken> findByUserAndUsedFalse(User user);
}
