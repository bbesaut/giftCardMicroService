package com.finovago.p2p.unit;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.finovago.p2p.repository.LedgerDiscrepancy;
import com.finovago.p2p.scheduler.LedgerReconciliationScheduler;
import com.finovago.p2p.service.LedgerService;

@ExtendWith(MockitoExtension.class)
class LedgerReconciliationSchedulerUnitTest {

    @Mock
    private LedgerService ledgerService;

    private LedgerReconciliationScheduler scheduler;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        scheduler = new LedgerReconciliationScheduler(ledgerService);

        Logger logger = (Logger) LoggerFactory.getLogger(LedgerReconciliationScheduler.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        Logger logger = (Logger) LoggerFactory.getLogger(LedgerReconciliationScheduler.class);
        logger.detachAppender(logAppender);
    }

    private static LedgerDiscrepancy discrepancy(Long giftCardId, String cardCode, Long merchantId, BigDecimal actual, BigDecimal theoretical) {
        LedgerDiscrepancy d = mock(LedgerDiscrepancy.class);
        when(d.getGiftCardId()).thenReturn(giftCardId);
        when(d.getCardCode()).thenReturn(cardCode);
        when(d.getMerchantId()).thenReturn(merchantId);
        when(d.getActualBalance()).thenReturn(actual);
        when(d.getTheoreticalBalance()).thenReturn(theoretical);
        return d;
    }

    @Test
    void reconcileBalances_noDiscrepancies_logsNothing() {
        when(ledgerService.findBalanceDiscrepancies()).thenReturn(List.of());

        scheduler.reconcileBalances();

        assertTrue(logAppender.list.isEmpty());
    }

    @Test
    void reconcileBalances_discrepancyFound_logsErrorWithCardDetails() {
        LedgerDiscrepancy discrepancy = discrepancy(1L, "GC-1", 42L, BigDecimal.valueOf(80.0), BigDecimal.valueOf(100.0));
        when(ledgerService.findBalanceDiscrepancies()).thenReturn(List.of(discrepancy));

        scheduler.reconcileBalances();

        assertEquals(1, logAppender.list.size());
        ILoggingEvent event = logAppender.list.get(0);
        assertEquals(Level.ERROR, event.getLevel());
        String message = event.getFormattedMessage();
        assertTrue(message.contains("GC-1"));
        assertTrue(message.contains("42"));
        assertTrue(message.contains("80.0"));
        assertTrue(message.contains("100.0"));
    }

    @Test
    void reconcileBalances_repositoryThrows_doesNotPropagate() {
        when(ledgerService.findBalanceDiscrepancies()).thenThrow(new RuntimeException("db unavailable"));

        scheduler.reconcileBalances();
    }
}
