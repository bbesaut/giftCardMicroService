package com.finovago.p2p.devseed;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class DevDatabaseResetterUnitTest {

    private JdbcTemplate jdbcTemplate;
    private DevDatabaseResetter resetter;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        SchemaOwnerJdbc ownerJdbc = mock(SchemaOwnerJdbc.class);
        when(ownerJdbc.template()).thenReturn(jdbcTemplate);
        resetter = new DevDatabaseResetter(ownerJdbc);
    }

    @Test
    void should_truncateEveryListedTableInOneStatement_when_tablesExist() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class))).thenReturn(List.of("gift_card", "merchants", "users"));

        resetter.reset();

        verify(jdbcTemplate).execute("TRUNCATE TABLE gift_card, merchants, users RESTART IDENTITY CASCADE");
    }

    @Test
    void should_notRunAnyStatement_when_thereIsNothingToTruncate() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class))).thenReturn(List.of());

        resetter.reset();

        verify(jdbcTemplate, never()).execute(anyString());
    }

    @Test
    void should_leaveFlywayHistoryAndPartitionsOutOfTheWipe() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class))).thenReturn(List.of("users"));

        resetter.reset();

        ArgumentCaptor<String> listTablesSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(listTablesSql.capture(), eq(String.class));
        // Wiping the migration history would make Flyway replay everything on the next start; partitions
        // are covered by truncating their parent, so listing them too would be redundant.
        assertTrue(listTablesSql.getValue().contains("c.relname <> 'flyway_schema_history'"));
        assertTrue(listTablesSql.getValue().contains("NOT c.relispartition"));
    }
}
