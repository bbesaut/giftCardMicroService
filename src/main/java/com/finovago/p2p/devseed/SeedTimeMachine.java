package com.finovago.p2p.devseed;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Rewrites timestamps so freshly seeded data looks like it has a history. The services stamp
 * everything with "now" and offer no way around it - rightly, in production - so the seeder runs the
 * real operations first and then moves the rows they wrote to the moment they should have happened.
 * Needs the schema-owner role: the ledger is append-only for p2p_app.
 */
@Component
@Profile("dev & seed")
class SeedTimeMachine {

    private final JdbcTemplate jdbc;

    SeedTimeMachine(SchemaOwnerJdbc ownerJdbc) {
        this.jdbc = ownerJdbc.template();
    }

    long latestLedgerId() {
        Long id = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM gift_card_ledger", Long.class);
        return id == null ? 0L : id;
    }

    /**
     * Moves every ledger entry written after {@code afterId} to {@code timestamp}, returning their ids
     * in write order. Identity ids only ever grow, and the seeder runs one operation at a time, so
     * "everything after the last id I saw" is exactly the rows that operation just produced.
     */
    List<Long> moveLedgerEntriesAfter(long afterId, LocalDateTime timestamp) {
        return jdbc.queryForList(
                "UPDATE gift_card_ledger SET created_at = ? WHERE id > ? RETURNING id",
                Long.class, Timestamp.valueOf(timestamp), afterId);
    }

    void moveCreationEntry(Long merchantId, String cardCode, LocalDateTime timestamp) {
        jdbc.update("""
                UPDATE gift_card_ledger SET created_at = ?
                WHERE entry_type = 'CREATION'
                  AND gift_card_id = (SELECT id FROM gift_card WHERE merchant_id = ? AND card_code = ?)
                """, Timestamp.valueOf(timestamp), merchantId, cardCode);
    }

    void moveHold(Long holdId, LocalDateTime createdAt, LocalDateTime expiresAt) {
        jdbc.update("UPDATE gift_card_hold SET created_at = ?, expires_at = ? WHERE id = ?",
                Timestamp.valueOf(createdAt), Timestamp.valueOf(expiresAt), holdId);
    }

    void setCardExpiration(Long merchantId, String cardCode, LocalDate expirationDate) {
        jdbc.update("UPDATE gift_card SET expiration_date = ? WHERE merchant_id = ? AND card_code = ?",
                Date.valueOf(expirationDate), merchantId, cardCode);
    }
}
