package com.finovago.p2p.devseed;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.finovago.p2p.devseed.SeedCatalog.CardKind;

import jakarta.annotation.Nullable;

/**
 * A gift card the seeder created, with what it needs to know about it later.
 *
 * @param finalExpirationDate for {@link CardKind#EXPIRED} cards, the past date they're moved to once
 *                            their history is written (null for every other kind)
 */
record SeededCard(String code, CardKind kind, BigDecimal initialBalance, @Nullable LocalDate finalExpirationDate) {
}
