package com.finovago.p2p.unit;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.finovago.p2p.scheduler.IdempotencyKeyCleanupScheduler;
import com.finovago.p2p.service.IdempotencyKeyService;

@ExtendWith(MockitoExtension.class)
class IdempotencyKeyCleanupSchedulerUnitTest {

    @Mock
    private IdempotencyKeyService idempotencyKeyService;

    private IdempotencyKeyCleanupScheduler scheduler;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        scheduler = new IdempotencyKeyCleanupScheduler(idempotencyKeyService);

        Logger logger = (Logger) LoggerFactory.getLogger(IdempotencyKeyCleanupScheduler.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        Logger logger = (Logger) LoggerFactory.getLogger(IdempotencyKeyCleanupScheduler.class);
        logger.detachAppender(logAppender);
    }

    @Test
    void sweepExpiredKeys_delegatesToServiceWithCurrentTimestamp() {
        LocalDateTime before = LocalDateTime.now();

        scheduler.sweepExpiredKeys();

        LocalDateTime after = LocalDateTime.now();
        verify(idempotencyKeyService).deleteExpired(argThatWithinRange(before, after));
        assertTrue(logAppender.list.isEmpty());
    }

    @Test
    void sweepExpiredKeys_serviceThrows_doesNotPropagateAndLogsWarning() {
        doThrow(new RuntimeException("db unavailable")).when(idempotencyKeyService).deleteExpired(any());

        scheduler.sweepExpiredKeys();

        assertEquals(1, logAppender.list.size());
        ILoggingEvent event = logAppender.list.get(0);
        assertEquals(Level.WARN, event.getLevel());
        assertTrue(event.getFormattedMessage().contains("db unavailable"));
    }

    private static LocalDateTime argThatWithinRange(LocalDateTime before, LocalDateTime after) {
        return org.mockito.ArgumentMatchers.argThat(cutoff ->
                cutoff != null && !cutoff.isBefore(before) && !cutoff.isAfter(after));
    }
}
