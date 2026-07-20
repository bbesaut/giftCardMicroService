package com.finovago.p2p.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.finovago.p2p.model.Merchant;

public interface MerchantRepository extends JpaRepository<Merchant, Long> {
}
