package com.finovago.p2p.devseed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Entry point of the dev data reset: wipes the database, then loads the seed dataset. A one-shot
 * job, not part of a normal run: it only exists when the app is launched with the {@code seed}
 * profile (see {@code scripts/reset-dev.ps1}), does its work, then shuts the application down. A
 * regular {@code dev} start never touches the data.
 */
@Component
@Profile("dev & seed")
class DevSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevSeedRunner.class);

    private final DevDatabaseResetter resetter;
    private final DevDataSeeder seeder;
    private final ConfigurableApplicationContext context;

    DevSeedRunner(DevDatabaseResetter resetter, DevDataSeeder seeder, ConfigurableApplicationContext context) {
        this.resetter = resetter;
        this.seeder = seeder;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        int exitCode = 0;
        try {
            resetter.reset();
            seeder.seed();
            log.info("Dev data reset and seeding finished");
        } catch (RuntimeException e) {
            log.error("Dev data reset and seeding failed", e);
            exitCode = 1;
        }
        // Explicit System.exit: the app was started as a job, and a lingering non-daemon thread
        // (devtools' restart watcher, for one) would otherwise keep the JVM alive after the work is done.
        final int code = exitCode;
        System.exit(SpringApplication.exit(context, () -> code));
    }
}
