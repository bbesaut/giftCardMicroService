package com.finovago.p2p.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@Getter
@Table(name = "gift_card_ledger")
public class LedgerEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "gift_card_id", nullable = false)
    private GiftCard giftCard;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 20)
    private LedgerEntryType entryType;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "hold_id")
    private Long holdId;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    // Only set for REFUND: the id of the REDEMPTION entry this refund reverses. No FK - see V23
    // migration comment (partitioned table self-reference constraint).
    @Column(name = "related_entry_id")
    private Long relatedEntryId;

    // Operator-supplied justification. Mandatory (enforced in GiftCardCreditService, not here) for
    // ADJUSTMENT, optional for REFUND, unused for every other entry type.
    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public LedgerEntry(GiftCard giftCard, Long merchantId, LedgerEntryType entryType, BigDecimal amount, BigDecimal balanceAfter, @Nullable Long holdId) {
        this(giftCard, merchantId, entryType, amount, balanceAfter, holdId, null);
    }

    public LedgerEntry(GiftCard giftCard, Long merchantId, LedgerEntryType entryType, BigDecimal amount, BigDecimal balanceAfter, @Nullable Long holdId, @Nullable Long actorUserId) {
        this(giftCard, merchantId, entryType, amount, balanceAfter, holdId, actorUserId, null, null);
    }

    public LedgerEntry(GiftCard giftCard, Long merchantId, LedgerEntryType entryType, BigDecimal amount, BigDecimal balanceAfter, @Nullable Long holdId, @Nullable Long actorUserId, @Nullable Long relatedEntryId, @Nullable String reason) {
        this.giftCard = giftCard;
        this.merchantId = merchantId;
        this.entryType = entryType;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.holdId = holdId;
        this.actorUserId = actorUserId;
        this.relatedEntryId = relatedEntryId;
        this.reason = reason;
        this.createdAt = LocalDateTime.now();
    }
}
