package com.finovago.p2p.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.finovago.p2p.model.GiftCard;
import com.finovago.p2p.model.LedgerEntry;
import com.finovago.p2p.model.LedgerEntryType;
import com.finovago.p2p.repository.LedgerDiscrepancy;
import com.finovago.p2p.repository.LedgerEntryRepository;

@Service
public class LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerService(LedgerEntryRepository ledgerEntryRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    /**
     * Appends a ledger row. Never call this outside the same transaction as the balance
     * mutation it records - the two must commit or roll back together.
     */
    public void record(GiftCard giftCard, Long merchantId, LedgerEntryType entryType, BigDecimal amount, BigDecimal balanceAfter, Long holdId, Long actorUserId) {
        ledgerEntryRepository.save(new LedgerEntry(giftCard, merchantId, entryType, amount, balanceAfter, holdId, actorUserId));
    }

    /** Gift cards whose stored balance disagrees with what their own ledger says it should be. */
    public List<LedgerDiscrepancy> findBalanceDiscrepancies() {
        return ledgerEntryRepository.findBalanceDiscrepancies();
    }

    /** Full history for a single card, oldest first. Caller is responsible for tenant scoping. */
    public List<LedgerEntry> getEntriesForCard(Long giftCardId) {
        return ledgerEntryRepository.findByGiftCardIdOrderByCreatedAtAsc(giftCardId);
    }

    /** January 1st of the furthest-out dated partition already created for gift_card_ledger. */
    public Optional<LocalDate> findLatestLedgerPartitionYear() {
        return ledgerEntryRepository.findLatestLedgerPartitionYear();
    }

    /** Whether any row has ever landed in the DEFAULT catch-all partition (dated partitions ran out). */
    public boolean existsAnyRowInDefaultLedgerPartition() {
        return ledgerEntryRepository.existsAnyRowInDefaultLedgerPartition();
    }
}
