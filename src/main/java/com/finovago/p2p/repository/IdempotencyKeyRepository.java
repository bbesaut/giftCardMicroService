package com.finovago.p2p.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.finovago.p2p.model.IdempotencyKey;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

    Optional<IdempotencyKey> findByMerchantIdAndIdempotencyKey(Long merchantId, String idempotencyKey);

    // Unlocked read for the scheduler's periodic sweep of expired entries.
    List<IdempotencyKey> findByExpiresAtBefore(LocalDateTime cutoff);
}
