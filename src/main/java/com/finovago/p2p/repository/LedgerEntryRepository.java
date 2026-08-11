package com.finovago.p2p.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.finovago.p2p.model.LedgerEntry;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByGiftCardIdOrderByCreatedAtAsc(Long giftCardId);

    // gift_card_ledger is RANGE-partitioned by year (see V21); dated partitions follow the naming
    // convention gift_card_ledger_YYYY. This finds January 1st of the furthest-out one already
    // created, so LedgerPartitionMonitorScheduler can tell how much runway is left before a follow-up
    // migration is needed to extend the range (see gift_card_ledger_default's doc comment in V21).
    @Query(value = """
            SELECT MAX(make_date(CAST(SUBSTRING(c.relname FROM 'gift_card_ledger_(\\d{4})$') AS INTEGER), 1, 1))
            FROM pg_inherits i
            JOIN pg_class c ON c.oid = i.inhrelid
            JOIN pg_class p ON p.oid = i.inhparent
            WHERE p.relname = 'gift_card_ledger' AND c.relname ~ '^gift_card_ledger_\\d{4}$'
            """, nativeQuery = true)
    Optional<LocalDate> findLatestLedgerPartitionYear();

    // True once a row has ever landed in the DEFAULT partition - meaning the dated partitions above
    // ran out before a follow-up migration extended them, and this needs immediate attention.
    @Query(value = "SELECT EXISTS (SELECT 1 FROM gift_card_ledger_default LIMIT 1)", nativeQuery = true)
    boolean existsAnyRowInDefaultLedgerPartition();

    // balance_after on the most recent ledger row is always the balance that entry produced (for
    // HOLD_PLACED/HOLD_RELEASED it's simply the unchanged balance) - so it's always the theoretical
    // truth for that card, and any card where it disagrees with gift_card.balance has drifted.
    @Query(value = """
            SELECT gc.id AS giftCardId, gc.card_code AS cardCode, gc.merchant_id AS merchantId,
                   gc.balance AS actualBalance, le.balance_after AS theoreticalBalance
            FROM gift_card gc
            JOIN LATERAL (
                SELECT balance_after
                FROM gift_card_ledger
                WHERE gift_card_id = gc.id
                ORDER BY created_at DESC, id DESC
                LIMIT 1
            ) le ON true
            WHERE gc.balance <> le.balance_after
            """, nativeQuery = true)
    List<LedgerDiscrepancy> findBalanceDiscrepancies();
}
