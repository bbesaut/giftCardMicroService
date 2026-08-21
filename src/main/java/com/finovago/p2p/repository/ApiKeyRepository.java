package com.finovago.p2p.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.finovago.p2p.model.ApiKey;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {
    Optional<ApiKey> findByKeyPrefix(String keyPrefix);

    Optional<ApiKey> findByMerchant_Id(Long merchantId);
}
