package com.finovago.p2p.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.finovago.p2p.model.LedgerEntry;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByGiftCardIdOrderByCreatedAtAsc(Long giftCardId);
}
