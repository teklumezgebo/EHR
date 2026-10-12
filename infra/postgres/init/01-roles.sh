#!/bin/bash
# Creates the schema owner (Flyway) and the least-privilege runtime user (the API).
# Runs once, on an empty data directory, via docker-entrypoint-initdb.d.
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v owner_password="$EHR_OWNER_PASSWORD" \
  -v app_password="$EHR_APP_PASSWORD" <<'SQL'
CREATE ROLE ehr_owner LOGIN PASSWORD :'owner_password';
CREATE ROLE ehr_app LOGIN PASSWORD :'app_password';

-- The database owner also owns the public schema (pg_database_owner), so Flyway can create objects there.
ALTER DATABASE :"DBNAME" OWNER TO ehr_owner;
REVOKE ALL ON DATABASE :"DBNAME" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"DBNAME" TO ehr_app;

-- No DELETE: clinical records are amended, never removed.
ALTER DEFAULT PRIVILEGES FOR ROLE ehr_owner IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE ON TABLES TO ehr_app;
ALTER DEFAULT PRIVILEGES FOR ROLE ehr_owner IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO ehr_app;
SQL
