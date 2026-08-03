package com.finovago.p2p.model;

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

    @Column(nullable = false)
    private double amount;

    @Column(name = "balance_after", nullable = false)
    private double balanceAfter;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public LedgerEntry(GiftCard giftCard, Long merchantId, LedgerEntryType entryType, double amount, double balanceAfter, @Nullable Long referenceId) {
        this.giftCard = giftCard;
        this.merchantId = merchantId;
        this.entryType = entryType;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.referenceId = referenceId;
        this.createdAt = LocalDateTime.now();
    }
}
