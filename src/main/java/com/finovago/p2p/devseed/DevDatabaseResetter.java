package com.finovago.p2p.devseed;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Empties every table of the local dev database, keeping the schema and Flyway's history intact.
 * Deliberately triggered by a command the developer runs (see {@link DevSeedRunner}), not by an
 * HTTP endpoint: a "wipe the database" route in the deployed API is one misconfiguration away from
 * production.
 */
@Component
@Profile("dev & seed")
class DevDatabaseResetter {

    private static final Logger log = LoggerFactory.getLogger(DevDatabaseResetter.class);

    // Read from the catalog rather than hard-coded, so a table added by a future migration is wiped
    // without touching this class. Partitions are skipped: truncating the partitioned parent (the
    // ledger) already covers them. quote_ident keeps the joined TRUNCATE below safe for any name.
    private static final String LIST_TABLES_SQL = """
            SELECT quote_ident(c.relname)
            FROM pg_class c
            JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = 'public'
              AND c.relkind IN ('r', 'p')
              AND NOT c.relispartition
              AND c.relname <> 'flyway_schema_history'
            ORDER BY c.relname
            """;

    private final SchemaOwnerJdbc ownerJdbc;

    DevDatabaseResetter(SchemaOwnerJdbc ownerJdbc) {
        this.ownerJdbc = ownerJdbc;
    }

    void reset() {
        List<String> tables = ownerJdbc.template().queryForList(LIST_TABLES_SQL, String.class);
        if (tables.isEmpty()) {
            return;
        }
        // One statement, so Postgres handles FK ordering itself; RESTART IDENTITY makes ids
        // deterministic across resets.
        ownerJdbc.template().execute("TRUNCATE TABLE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE");
        log.warn("Dev database reset: truncated {} tables ({})", tables.size(), String.join(", ", tables));
    }
}
