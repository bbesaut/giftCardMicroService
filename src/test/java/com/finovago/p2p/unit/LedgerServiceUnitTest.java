package com.finovago.p2p.unit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.finovago.p2p.model.GiftCard;
import com.finovago.p2p.model.LedgerEntry;
import com.finovago.p2p.model.LedgerEntryType;
import com.finovago.p2p.model.Merchant;
import com.finovago.p2p.model.User;
import com.finovago.p2p.repository.LedgerDiscrepancy;
import com.finovago.p2p.repository.LedgerEntryRepository;
import com.finovago.p2p.repository.UserRepository;
import com.finovago.p2p.service.LedgerService;

@ExtendWith(MockitoExtension.class)
class LedgerServiceUnitTest {

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private UserRepository userRepository;

    private LedgerService ledgerService;

    private GiftCard giftCard;

    @BeforeEach
    void setUp() {
        ledgerService = new LedgerService(ledgerEntryRepository, userRepository);
        Merchant merchant = new Merchant("Test Merchant", "merchant@example.com");
        giftCard = new GiftCard(merchant, "GC-1", BigDecimal.valueOf(100.0), true, LocalDate.now().plusDays(30));
    }

    @Test
    void record_savesLedgerEntry_withAllFieldsPopulated() {
        ledgerService.record(giftCard, 1L, LedgerEntryType.REDEMPTION, BigDecimal.valueOf(30.0), BigDecimal.valueOf(70.0), 42L, 7L, false);

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository).save(captor.capture());

        LedgerEntry saved = captor.getValue();
        assertEquals(giftCard, saved.getGiftCard());
        assertEquals(1L, saved.getMerchantId());
        assertEquals(LedgerEntryType.REDEMPTION, saved.getEntryType());
        assertEquals(0, BigDecimal.valueOf(30.0).compareTo(saved.getAmount()));
        assertEquals(0, BigDecimal.valueOf(70.0).compareTo(saved.getBalanceAfter()));
        assertEquals(42L, saved.getHoldId());
        assertEquals(7L, saved.getActorUserId());
        assertEquals(false, saved.isActorViaApiKey());
    }

    @Test
    void record_savesLedgerEntry_withNullHoldId() {
        ledgerService.record(giftCard, 1L, LedgerEntryType.CREATION, BigDecimal.valueOf(100.0), BigDecimal.valueOf(100.0), null, null, false);

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository).save(captor.capture());

        assertNull(captor.getValue().getHoldId());
        assertNull(captor.getValue().getActorUserId());
    }

    @Test
    void record_flagsEntryAsViaApiKey_whenCallerPassesApiKeyFlag() {
        ledgerService.record(giftCard, 1L, LedgerEntryType.REDEMPTION, BigDecimal.valueOf(30.0), BigDecimal.valueOf(70.0), null, null, true);

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository).save(captor.capture());

        assertEquals(true, captor.getValue().isActorViaApiKey());
        assertNull(captor.getValue().getActorUserId());
    }

    @Test
    void findBalanceDiscrepancies_delegatesToRepository() {
        List<LedgerDiscrepancy> discrepancies = List.of(mock(LedgerDiscrepancy.class));
        when(ledgerEntryRepository.findBalanceDiscrepancies()).thenReturn(discrepancies);

        assertSame(discrepancies, ledgerService.findBalanceDiscrepancies());
    }

    @Test
    void resolveActors_returnsPlainEmail_forRegularUser() {
        User regularUser = mock(User.class);
        when(regularUser.getId()).thenReturn(7L);
        when(regularUser.getEmail()).thenReturn("employee@example.com");
        LedgerEntry entry = new LedgerEntry(giftCard, 1L, LedgerEntryType.REDEMPTION, BigDecimal.valueOf(30.0), BigDecimal.valueOf(70.0), null, 7L);
        when(userRepository.findAllById(java.util.Set.of(7L))).thenReturn(List.of(regularUser));

        Map<Long, String> actors = ledgerService.resolveActors(List.of(entry));

        assertEquals("employee@example.com", actors.get(7L));
    }

    @Test
    void resolveActors_omitsEntry_whenActorUserIdIsNull() {
        LedgerEntry entry = new LedgerEntry(giftCard, 1L, LedgerEntryType.CREATION, BigDecimal.valueOf(100.0), BigDecimal.valueOf(100.0), null, null);

        Map<Long, String> actors = ledgerService.resolveActors(List.of(entry));

        assertTrue(actors.isEmpty());
    }

    @Test
    void resolveActors_omitsEntry_whenActorUserWasDeleted() {
        LedgerEntry entry = new LedgerEntry(giftCard, 1L, LedgerEntryType.CREATION, BigDecimal.valueOf(100.0), BigDecimal.valueOf(100.0), null, 99L);
        when(userRepository.findAllById(java.util.Set.of(99L))).thenReturn(List.of());

        Map<Long, String> actors = ledgerService.resolveActors(List.of(entry));

        assertTrue(actors.isEmpty());
    }
}
