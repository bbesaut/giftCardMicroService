package com.finovago.p2p.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.finovago.p2p.model.ApiKey;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {
    Optional<ApiKey> findByKeyPrefix(String keyPrefix);

    Optional<ApiKey> findByMerchant_Id(Long merchantId);

    /**
     * Targeted update instead of save(entity) - resolve() may be updating a cached ApiKey loaded
     * in a previous transaction, and save()'s merge() on a cross-session detached entity would
     * otherwise force an extra SELECT-by-id before the UPDATE, defeating the point of caching it.
     */
    @Modifying
    @Query("update ApiKey a set a.lastUsedAt = :lastUsedAt where a.id = :id")
    void updateLastUsedAt(@Param("id") Long id, @Param("lastUsedAt") LocalDateTime lastUsedAt);
}
