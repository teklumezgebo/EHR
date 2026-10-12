-- Default privileges also cover Flyway's history table; the runtime user must not edit migration history.
REVOKE ALL ON flyway_schema_history FROM ehr_app;
