package com.finovago.p2p.unit;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.finovago.p2p.exception.ExpiredGiftCardException;
import com.finovago.p2p.exception.InactiveGiftCardException;
import com.finovago.p2p.model.GiftCard;
import com.finovago.p2p.model.Merchant;

class GiftCardUnitTest {

    private final Merchant merchant = new Merchant("Test Merchant", "merchant@example.com");

    @Test
    void should_not_throw_when_card_is_active_and_not_expired() {
        GiftCard card = new GiftCard(merchant, "GC-1", BigDecimal.valueOf(100.0), true, LocalDate.now().plusDays(30));

        assertDoesNotThrow(card::ensureUsable);
    }

    @Test
    void should_throw_when_card_is_inactive() {
        GiftCard card = new GiftCard(merchant, "GC-2", BigDecimal.valueOf(100.0), false, LocalDate.now().plusDays(30));

        assertThrows(InactiveGiftCardException.class, card::ensureUsable);
    }

    @Test
    void should_throw_when_card_is_expired() {
        GiftCard card = new GiftCard(merchant, "GC-3", BigDecimal.valueOf(100.0), true, LocalDate.now().minusDays(1));

        assertThrows(ExpiredGiftCardException.class, card::ensureUsable);
    }

    @Test
    void should_check_active_before_expiration() {
        GiftCard card = new GiftCard(merchant, "GC-4", BigDecimal.valueOf(100.0), false, LocalDate.now().minusDays(1));

        assertThrows(InactiveGiftCardException.class, card::ensureUsable);
    }
}
