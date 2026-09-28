package com.finovago.p2p.devseed;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Wires the dev seeding tools. Doubly gated - both the {@code dev} and the {@code seed} profile must
 * be active - so none of these beans exist in prod or test, nor in a plain local run.
 */
@Configuration
@Profile("dev & seed")
class DevSeedConfig {

    @Bean
    SchemaOwnerJdbc schemaOwnerJdbc(
            @Value("${spring.flyway.url}") String ownerUrl,
            @Value("${spring.flyway.user}") String ownerUser,
            @Value("${spring.flyway.password}") String ownerPassword,
            @Value("${spring.datasource.url}") String appUrl) {
        // Both URLs: the wipe runs over the owner connection, but the app must also be pointed at
        // the local database, otherwise the seeded data wouldn't even land where the app reads.
        LocalDatabaseGuard.requireLocal(ownerUrl);
        LocalDatabaseGuard.requireLocal(appUrl);
        return new SchemaOwnerJdbc(ownerUrl, ownerUser, ownerPassword);
    }
}
