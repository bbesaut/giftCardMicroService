package com.finovago.p2p.devseed;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * JDBC access as the schema-owner role (the one Flyway uses), not as p2p_app. Dev seeding needs it
 * because p2p_app is deliberately least-privilege (V17): it can't TRUNCATE anything, nor UPDATE the
 * append-only ledger to backdate entries.
 * <p>
 * Holds one connection for the whole job rather than opening one per statement: seeding the
 * activity history issues thousands of small updates, and connecting is the expensive part.
 * <p>
 * A dedicated wrapper type rather than a plain {@code JdbcTemplate} or {@code DataSource} bean:
 * Spring Boot's own auto-configuration backs off when one already exists, which would silently swap
 * the application's connection for this privileged one.
 */
final class SchemaOwnerJdbc implements AutoCloseable {

    private final SingleConnectionDataSource dataSource;
    private final JdbcTemplate template;

    SchemaOwnerJdbc(String url, String user, String password) {
        this.dataSource = new SingleConnectionDataSource(url, user, password, true);
        this.template = new JdbcTemplate(dataSource);
    }

    JdbcTemplate template() {
        return template;
    }

    @Override
    public void close() {
        dataSource.destroy();
    }
}
