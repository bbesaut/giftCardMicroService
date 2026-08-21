package com.finovago.p2p.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@NoArgsConstructor
@Getter
@Table(name = "api_keys")
public class ApiKey {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "merchant_id", nullable = false, unique = true)
    private Merchant merchant;

    @Column(name = "key_prefix", nullable = false, unique = true)
    private String keyPrefix;

    @Setter
    @Column(name = "hashed_secret", nullable = false)
    private String hashedSecret;

    @Setter
    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Setter
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Setter
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    public ApiKey(Merchant merchant, String keyPrefix, String hashedSecret) {
        this.merchant = merchant;
        this.keyPrefix = keyPrefix;
        this.hashedSecret = hashedSecret;
        this.active = true;
        this.createdAt = LocalDateTime.now();
    }

    public void rotate(String newKeyPrefix, String newHashedSecret) {
        this.keyPrefix = newKeyPrefix;
        this.hashedSecret = newHashedSecret;
        this.active = true;
        this.revokedAt = null;
    }
}
