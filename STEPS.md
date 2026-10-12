# Build Sequence

First milestone: log in → open a patient → see the Storyboard sidebar, with the access
audited, tested, and running in CI. Access model is role + care team; caretaker
delegation is out of scope.

## 0. Repo layout and hygiene
- [x] `api/` Spring Boot, `web/` React, `infra/keycloak/` realm file.
- [x] `compose.yaml` at the root (created in step 2).
- [x] `.gitignore` for Gradle, Java, Node/Vite, env files, editors, and macOS.

## 1. Backend skeleton
- [x] Generate from start.spring.io into `api/`: Spring Boot 4, Java 21, Gradle Kotlin DSL,
      Web, Security, OAuth2 Resource Server, Data JPA, Flyway, PostgreSQL, Validation,
      Actuator, Testcontainers.
- [x] Confirm every dependency supports Boot 4.
- [x] Defer HAPI FHIR to Phase 3 (Boot 4 uses Jackson 3; HAPI is built on Jackson 2).
- [x] Add Spring Modulith with a module-structure test so package boundaries are enforced.

## 2. Local services
- [x] `compose.yaml` with Postgres 16 and Keycloak.
- [x] Realm file in `infra/keycloak/`, imported on startup: two roles
      (PHYSICIAN, MA_NURSE) and one demo user each.

## 3. Database users
- [x] `ehr_owner` runs Flyway and owns the schema.
- [x] `ehr_app` is the runtime user: SELECT, INSERT, UPDATE on tables (no DELETE; records are amended),
      INSERT and SELECT only on `audit_event` (revoked in V1).

## 4. First migration (`V1__core.sql`)
- [x] `patient`, `app_user` (Keycloak subject → dept; role stays in the JWT), `care_team_member`,
      `audit_event` with the no-update/no-delete trigger and `prev_hash` chain.
- [x] `encounter` in its own later migration (`V2__encounter.sql`).
- [x] Dev-only seed data in `db/seed`, loaded by the `dev` profile only.

## 5. Security skeleton
- [x] Resource server validating Keycloak JWTs (signature, issuer, `ehr-api` audience); map
      `realm_access.roles` to `ROLE_*` authorities.
- [x] Most tests use `spring-security-test` JWTs; one Testcontainers Keycloak test covers
      the real token path.

## 6. Core slice
- [x] `GET /api/patients/{id}` guarded by `AccessPolicy` (role + care team) and `@PhiAccess`.
- [x] Audit writes use a separate transaction (`REQUIRES_NEW`), so denied rows survive the rollback.
- [x] Serialize audit inserts (advisory lock in the V1 insert trigger) so the hash chain stays linear.
- [x] Tests: care-team physician allowed, other physician denied, both audited.

## 7. CI
- [ ] GitHub Actions running `./gradlew test` on every push.

## 8. Frontend
- [ ] Scaffold `web/` with Vite + React + TypeScript + Tailwind + TanStack Query.
- [ ] Keycloak login, then the Storyboard sidebar reading `GET /api/patients/{id}`.
