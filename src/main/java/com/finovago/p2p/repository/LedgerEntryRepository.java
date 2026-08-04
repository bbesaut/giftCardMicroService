package com.finovago.p2p.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.finovago.p2p.model.LedgerEntry;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByGiftCardIdOrderByCreatedAtAsc(Long giftCardId);

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
