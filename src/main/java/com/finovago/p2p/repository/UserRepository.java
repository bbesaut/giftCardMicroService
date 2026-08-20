package com.finovago.p2p.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.finovago.p2p.model.User;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    Optional<User> findByIdAndMerchant_Id(Long id, Long merchantId);
}