package com.finovago.p2p.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.finovago.p2p.exception.ExpiredGiftCardException;
import com.finovago.p2p.exception.InactiveGiftCardException;

@Entity
@NoArgsConstructor
@Getter
@Table(name = "gift_card", uniqueConstraints = @UniqueConstraint(name = "uq_giftcard_merchant_cardcode", columnNames = {"merchant_id", "card_code"}))
public class GiftCard
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String cardCode;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;
    private boolean active;
    private LocalDate expirationDate;

    @ManyToOne(optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    public GiftCard(Merchant merchant, String cardCode, BigDecimal balance, boolean active, @Nullable LocalDate expirationDate) {
        this.merchant = merchant;
        this.cardCode = cardCode;
        this.balance = balance;
        this.active = active;
        this.expirationDate = expirationDate != null ? expirationDate : LocalDate.now().plusYears(2);
    }

    public void ensureUsable() {
        if (!active) {
            throw new InactiveGiftCardException("Card is already inactive");
        }
        if (expirationDate.isBefore(LocalDate.now())) {
            throw new ExpiredGiftCardException("Gift card has expired");
        }
    }

    public void deductBalance(BigDecimal amount) {
        this.balance = this.balance.subtract(amount);
    }

    public void drainCard() {
        this.balance = BigDecimal.ZERO.setScale(2);
        this.active = false;
    }
}
