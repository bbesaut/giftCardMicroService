package com.finovago.p2p.unit;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.finovago.p2p.scheduler.LedgerPartitionMonitorScheduler;
import com.finovago.p2p.service.LedgerService;

@ExtendWith(MockitoExtension.class)
class LedgerPartitionMonitorSchedulerUnitTest {

    @Mock
    private LedgerService ledgerService;

    private LedgerPartitionMonitorScheduler scheduler;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        scheduler = new LedgerPartitionMonitorScheduler(ledgerService);

        Logger logger = (Logger) LoggerFactory.getLogger(LedgerPartitionMonitorScheduler.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        Logger logger = (Logger) LoggerFactory.getLogger(LedgerPartitionMonitorScheduler.class);
        logger.detachAppender(logAppender);
    }

    @Test
    void checkPartitionRunway_plentyOfYearsRemaining_logsInfoSummary() {
        when(ledgerService.findLatestLedgerPartitionYear())
                .thenReturn(Optional.of(LocalDate.now().plusYears(2).withDayOfMonth(1)));
        when(ledgerService.existsAnyRowInDefaultLedgerPartition()).thenReturn(false);

        scheduler.checkPartitionRunway();

        assertEquals(1, logAppender.list.size());
        assertEquals(Level.INFO, logAppender.list.get(0).getLevel());
    }

    @Test
    void checkPartitionRunway_lessThanAYearRemaining_logsWarning() {
        when(ledgerService.findLatestLedgerPartitionYear())
                .thenReturn(Optional.of(LocalDate.now().plusMonths(6).withDayOfMonth(1)));
        when(ledgerService.existsAnyRowInDefaultLedgerPartition()).thenReturn(false);

        scheduler.checkPartitionRunway();

        assertEquals(1, logAppender.list.size());
        assertEquals(Level.WARN, logAppender.list.get(0).getLevel());
    }

    @Test
    void checkPartitionRunway_noDatedPartitionsFound_logsWarning() {
        when(ledgerService.findLatestLedgerPartitionYear()).thenReturn(Optional.empty());
        when(ledgerService.existsAnyRowInDefaultLedgerPartition()).thenReturn(false);

        scheduler.checkPartitionRunway();

        assertEquals(1, logAppender.list.size());
        assertEquals(Level.WARN, logAppender.list.get(0).getLevel());
    }

    @Test
    void checkPartitionRunway_defaultPartitionHasRows_logsError() {
        when(ledgerService.findLatestLedgerPartitionYear())
                .thenReturn(Optional.of(LocalDate.now().plusYears(2).withDayOfMonth(1)));
        when(ledgerService.existsAnyRowInDefaultLedgerPartition()).thenReturn(true);

        scheduler.checkPartitionRunway();

        assertEquals(2, logAppender.list.size());
        assertEquals(Level.INFO, logAppender.list.get(0).getLevel());
        assertEquals(Level.ERROR, logAppender.list.get(1).getLevel());
    }

    @Test
    void checkPartitionRunway_serviceThrows_doesNotPropagate() {
        when(ledgerService.findLatestLedgerPartitionYear()).thenThrow(new RuntimeException("db unavailable"));

        scheduler.checkPartitionRunway();
    }
}
