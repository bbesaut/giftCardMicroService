package com.finovago.p2p.model;

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
import lombok.Setter;

@Entity
@NoArgsConstructor
@Getter
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    private Role role;

    @ManyToOne
    @JoinColumn(name = "merchant_id")
    private Merchant merchant;

    @Column(name = "is_owner", nullable = false)
    private boolean owner;

    @Setter
    @Column(nullable = false)
    private boolean active;

    public User(String email, String password, Role role, @Nullable Merchant merchant) {
        this(email, password, role, merchant, false);
    }

    public User(String email, String password, Role role, @Nullable Merchant merchant, boolean owner) {
        this.email = email;
        this.password = password;
        this.role = role;
        this.merchant = merchant;
        this.owner = owner;
        this.active = true;
    }
}

