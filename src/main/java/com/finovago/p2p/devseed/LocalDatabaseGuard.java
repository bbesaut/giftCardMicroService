package com.finovago.p2p.devseed;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

/**
 * Last line of defence for the dev-only seeding tools, which run with schema-owner credentials and
 * wipe every table: refuses any JDBC URL that doesn't point at the local machine. Fails closed - an
 * unparseable URL, a missing host or a multi-host URL is rejected, never waved through.
 */
final class LocalDatabaseGuard {

    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");
    private static final String JDBC_PREFIX = "jdbc:";

    private LocalDatabaseGuard() {
    }

    static void requireLocal(String jdbcUrl) {
        if (!isLocal(jdbcUrl)) {
            throw new IllegalStateException(
                    "Refusing to run dev data seeding: '" + jdbcUrl + "' is not a local database (allowed hosts: "
                            + LOCAL_HOSTS + ")");
        }
    }

    static boolean isLocal(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith(JDBC_PREFIX)) {
            return false;
        }
        try {
            String host = new URI(jdbcUrl.substring(JDBC_PREFIX.length())).getHost();
            return host != null && LOCAL_HOSTS.contains(host.toLowerCase());
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
