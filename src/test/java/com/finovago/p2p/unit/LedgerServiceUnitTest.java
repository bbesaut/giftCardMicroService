package com.finovago.p2p.unit;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.finovago.p2p.model.GiftCard;
import com.finovago.p2p.model.LedgerEntry;
import com.finovago.p2p.model.LedgerEntryType;
import com.finovago.p2p.model.Merchant;
import com.finovago.p2p.repository.LedgerEntryRepository;
import com.finovago.p2p.service.LedgerService;

@ExtendWith(MockitoExtension.class)
class LedgerServiceUnitTest {

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    private LedgerService ledgerService;

    private GiftCard giftCard;

    @BeforeEach
    void setUp() {
        ledgerService = new LedgerService(ledgerEntryRepository);
        Merchant merchant = new Merchant("Test Merchant", "merchant@example.com");
        giftCard = new GiftCard(merchant, "GC-1", BigDecimal.valueOf(100.0), true, LocalDate.now().plusDays(30));
    }

    @Test
    void record_savesLedgerEntry_withAllFieldsPopulated() {
        ledgerService.record(giftCard, 1L, LedgerEntryType.REDEMPTION, BigDecimal.valueOf(30.0), BigDecimal.valueOf(70.0), 42L);

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository).save(captor.capture());

        LedgerEntry saved = captor.getValue();
        assertEquals(giftCard, saved.getGiftCard());
        assertEquals(1L, saved.getMerchantId());
        assertEquals(LedgerEntryType.REDEMPTION, saved.getEntryType());
        assertEquals(0, BigDecimal.valueOf(30.0).compareTo(saved.getAmount()));
        assertEquals(0, BigDecimal.valueOf(70.0).compareTo(saved.getBalanceAfter()));
        assertEquals(42L, saved.getReferenceId());
    }

    @Test
    void record_savesLedgerEntry_withNullReferenceId() {
        ledgerService.record(giftCard, 1L, LedgerEntryType.CREATION, BigDecimal.valueOf(100.0), BigDecimal.valueOf(100.0), null);

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository).save(captor.capture());

        assertNull(captor.getValue().getReferenceId());
    }
}
