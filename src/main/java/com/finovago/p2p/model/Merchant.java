package com.finovago.p2p.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@NoArgsConstructor
@Getter
@Table(name = "merchants")
public class Merchant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String contactEmail;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private Instant createdAt;

    /** Requests/minute override for the redeem/lookup/reserve rate limit. Null = use the app-wide default. */
    @Setter
    @Column(name = "rate_limit_capacity")
    private Integer rateLimitCapacity;

    public Merchant(String name, String contactEmail) {
        this.name = name;
        this.contactEmail = contactEmail;
        this.active = true;
        this.createdAt = Instant.now();
    }
}
