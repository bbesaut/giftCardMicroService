package com.finovago.p2p.model;

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
    private double balance;
    private boolean active;
    private LocalDate expirationDate;

    @ManyToOne(optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    public GiftCard(Merchant merchant, String cardCode, double balance, boolean active, @Nullable LocalDate expirationDate) {
        this.merchant = merchant;
        this.cardCode = cardCode;
        this.balance = balance;
        this.active = active;
        this.expirationDate = expirationDate != null ? expirationDate : LocalDate.now().plusYears(2);
    }

    public void deductBalance(double amount) {
        this.balance -= amount;
    }

    public void drainCard() {
        this.balance = 0.0;
        this.active = false;
    }
}
