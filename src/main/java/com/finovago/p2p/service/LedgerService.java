package com.finovago.p2p.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.finovago.p2p.model.GiftCard;
import com.finovago.p2p.model.LedgerEntry;
import com.finovago.p2p.model.LedgerEntryType;
import com.finovago.p2p.model.User;
import com.finovago.p2p.repository.LedgerDiscrepancy;
import com.finovago.p2p.repository.LedgerEntryRepository;
import com.finovago.p2p.repository.UserRepository;

@Service
public class LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final UserRepository userRepository;

    public LedgerService(LedgerEntryRepository ledgerEntryRepository, UserRepository userRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.userRepository = userRepository;
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

    /**
     * Batch-resolves entries' actorUserId to a display string ("SYSTEM / email" for service
     * accounts, plain email otherwise), one query for the whole list instead of N+1.
     * Entries whose actorUserId is missing from the result have no row in the map - callers
     * distinguish "no actor recorded" (null actorUserId) from "actor since deleted" that way.
     */
    public Map<Long, String> resolveActors(List<LedgerEntry> entries) {
        Set<Long> actorUserIds = entries.stream()
                .map(LedgerEntry::getActorUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (actorUserIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(actorUserIds).stream()
                .collect(Collectors.toMap(User::getId, LedgerService::formatActor));
    }

    private static String formatActor(User user) {
        return user.isServiceAccount() ? "SYSTEM / " + user.getEmail() : user.getEmail();
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
