-- Automates future gift_card_ledger partition creation using pg_partman, scheduled via pg_cron.
-- V21 pre-created partitions manually through 2029; from here on, pg_partman takes over creating
-- new yearly partitions as time advances, so a yearly manual Flyway migration is no longer needed.
-- LedgerPartitionMonitorScheduler is kept as-is: it now acts as a safety net that catches this
-- automation silently failing (e.g. the cron job stops running), rather than as the only signal.
--
-- pg_cron/pg_partman are only available on Neon in this project: the plain postgres:17 image used
-- by Testcontainers (integration-tests profile) and docker-compose (dev) doesn't bundle them. Every
-- step below is guarded to no-op wherever the extensions aren't installed, so this migration is a
-- no-op locally/in CI - the automation itself was validated by hand against a Neon branch, not by
-- the test suite.
--
-- Prerequisites, done out-of-band before this migration runs against an environment that has these
-- extensions available (i.e. prod, once):
--   1. cron.database_name set to this database's name via the Neon API (endpoint PATCH,
--      pg_settings.cron.database_name), followed by a compute restart. pg_cron on Neon can only run
--      in the database named by that setting - not something SQL/Flyway can configure.
--   2. The partman_admin role must already exist (created via Neon console, real password - never
--      committed here, same reasoning as p2p_app in V17).

-- Extensions: installed only where available.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'pg_cron') THEN
        EXECUTE 'CREATE EXTENSION IF NOT EXISTS pg_cron';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'pg_partman') THEN
        EXECUTE 'CREATE EXTENSION IF NOT EXISTS pg_partman';
    END IF;
END $$;

-- pg_partman's introspection (show_partitions) can't parse a MINVALUE lower bound, which is what
-- V21 used for the catch-all historical partition. Replace it with an explicit, sufficiently early
-- bound instead - equivalent in practice (no ledger row predates this service's existence), but
-- readable by pg_partman. Applied unconditionally (harmless without pg_partman) to keep schema
-- shape identical across environments.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'gift_card_ledger_historical') THEN
        EXECUTE 'ALTER TABLE gift_card_ledger DETACH PARTITION gift_card_ledger_historical';
        EXECUTE 'ALTER TABLE gift_card_ledger ATTACH PARTITION gift_card_ledger_historical '
                'FOR VALUES FROM (''1900-01-01'') TO (''2026-01-01'')';
    END IF;
END $$;

-- Hand future partitions off to pg_partman, starting exactly where V21's manual partitions leave
-- off (2030) so it never touches the existing 2026-2029/historical/default partitions.
-- p_default_table => false: V21 already created gift_card_ledger_default; pg_partman would
-- otherwise try (and fail) to create its own.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_partman') THEN
        -- Nested IF (not combined with AND above): Postgres doesn't guarantee short-circuit
        -- evaluation, and public.part_config only exists once pg_partman is installed.
        IF NOT EXISTS (SELECT 1 FROM public.part_config WHERE parent_table = 'public.gift_card_ledger') THEN
            PERFORM public.create_parent(
                p_parent_table => 'public.gift_card_ledger',
                p_control => 'created_at',
                p_interval => '1 year',
                p_premake => 3,
                p_start_partition => '2030-01-01',
                p_default_table => false
            );
        END IF;
    END IF;
END $$;

-- Narrow entry point for the scheduled maintenance job. SECURITY DEFINER so it runs with the
-- owning (migration) role's rights - attaching a new partition requires table ownership, which the
-- least-privilege partman_admin role deliberately doesn't have. partman_admin can only ever call
-- this one function, nothing broader.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_partman') THEN
        EXECUTE $ddl$
            CREATE OR REPLACE FUNCTION public.run_ledger_partition_maintenance()
            RETURNS void
            LANGUAGE plpgsql
            SECURITY DEFINER
            SET search_path = public
            AS $func$
            BEGIN
                PERFORM public.run_maintenance('public.gift_card_ledger');
            END;
            $func$
        $ddl$;

        EXECUTE 'REVOKE ALL ON FUNCTION public.run_ledger_partition_maintenance() FROM PUBLIC';
        EXECUTE 'GRANT EXECUTE ON FUNCTION public.run_ledger_partition_maintenance() TO partman_admin';
    END IF;
END $$;

-- Scheduled by the migration (owner) role since only it has USAGE on Neon's cron schema, then
-- immediately reassigned to partman_admin so the job actually executes under the least-privilege
-- role going forward, not the owner.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_cron')
       AND EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_partman') THEN
        IF NOT EXISTS (SELECT 1 FROM cron.job WHERE jobname = 'partman-maintenance-ledger') THEN
            PERFORM cron.schedule(
                'partman-maintenance-ledger',
                '0 3 * * *',
                'SELECT public.run_ledger_partition_maintenance();'
            );
        END IF;
        UPDATE cron.job SET username = 'partman_admin' WHERE jobname = 'partman-maintenance-ledger';
    END IF;
END $$;
