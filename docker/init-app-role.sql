-- Runs automatically on first container init (docker-entrypoint-initdb.d), before Flyway ever connects.
-- Creates the restricted runtime role that V17__restrict_app_role_privileges.sql later grants
-- table-level privileges to. Dev-only throwaway credentials, consistent with the existing p2p/p2p
-- superuser used for migrations in docker-compose.yml.
CREATE ROLE p2p_app LOGIN PASSWORD 'p2p_app';
