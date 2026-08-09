package com.finovago.p2p.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
@Table(name = "gift_card_hold")
public class GiftCardHold {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "gift_card_id", nullable = false)
    private GiftCard giftCard;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HoldStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public GiftCardHold(GiftCard giftCard, Long merchantId, BigDecimal amount, LocalDateTime expiresAt) {
        this.giftCard = giftCard;
        this.merchantId = merchantId;
        this.amount = amount;
        this.status = HoldStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.expiresAt = expiresAt;
    }

    public void capture() {
        this.status = HoldStatus.CAPTURED;
    }

    public void release() {
        this.status = HoldStatus.RELEASED;
    }

    public void expire() {
        this.status = HoldStatus.EXPIRED;
    }

    public boolean isPending() {
        return this.status == HoldStatus.PENDING;
    }
}
