package com.finovago.p2p.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@Getter
@Table(name = "idempotency_key")
public class IdempotencyKey {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IdempotencyStatus status;

    @Column(name = "response_status")
    private String responseStatus;

    @Column(name = "deducted_amount")
    private Double deductedAmount;

    @Column(name = "remaining_balance")
    private Double remainingBalance;

    @Column(name = "remaining_to_pay")
    private Double remainingToPay;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public IdempotencyKey(Long merchantId, String idempotencyKey, String requestHash, LocalDateTime expiresAt) {
        this.merchantId = merchantId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.status = IdempotencyStatus.IN_PROGRESS;
        this.createdAt = LocalDateTime.now();
        this.expiresAt = expiresAt;
    }

    public boolean matchesRequest(String requestHash) {
        return this.requestHash.equals(requestHash);
    }

    public boolean isInProgress() {
        return this.status == IdempotencyStatus.IN_PROGRESS;
    }

    public void complete(String responseStatus, double deductedAmount, double remainingBalance, double remainingToPay) {
        this.status = IdempotencyStatus.COMPLETED;
        this.responseStatus = responseStatus;
        this.deductedAmount = deductedAmount;
        this.remainingBalance = remainingBalance;
        this.remainingToPay = remainingToPay;
    }
}
