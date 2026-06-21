package com.finovago.p2p.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@Getter
public class GiftCard
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true, nullable = false)
    private String cardCode;
    private double balance;
    private boolean active;

    public GiftCard(String cardCode, double balance, boolean active) {
        this.cardCode = cardCode;
        this.balance = balance;
        this.active = active;
    }

    public void deductBalance(double amount) {
        this.balance -= amount;
    }

    public void drainCard() {
        this.balance = 0.0;
        this.active = false;
    }
}
