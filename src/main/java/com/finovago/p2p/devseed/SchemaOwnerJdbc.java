package com.finovago.p2p.devseed;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * JDBC access as the schema-owner role (the one Flyway uses), not as p2p_app. Dev seeding needs it
 * because p2p_app is deliberately least-privilege (V17): it can't TRUNCATE anything, nor UPDATE the
 * append-only ledger to backdate entries.
 * <p>
 * A dedicated wrapper type rather than a plain {@code JdbcTemplate} bean: Spring Boot's own
 * JdbcTemplate auto-configuration backs off when one already exists, which would silently swap the
 * application's connection for this privileged one.
 */
record SchemaOwnerJdbc(JdbcTemplate template) {
}
