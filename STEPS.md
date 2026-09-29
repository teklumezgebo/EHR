# Build Sequence

First milestone: log in → open a patient → see the Storyboard sidebar, with the access
audited, tested, and running in CI. Access model is role + care team; caretaker
delegation is out of scope.

## 0. Repo layout and hygiene
- [x] `api/` Spring Boot, `web/` React, `infra/keycloak/` realm file.
- [ ] `compose.yaml` at the root (created in step 2).
- [x] `.gitignore` for Gradle, Java, Node/Vite, env files, editors, and macOS.

## 1. Backend skeleton
- [x] Generate from start.spring.io into `api/`: Spring Boot 4, Java 21, Gradle Kotlin DSL,
      Web, Security, OAuth2 Resource Server, Data JPA, Flyway, PostgreSQL, Validation,
      Actuator, Testcontainers.
- [ ] Confirm every dependency supports Boot 4 before generating.
- [ ] Defer HAPI FHIR to Phase 3 (Boot 4 uses Jackson 3; HAPI is built on Jackson 2).
- [ ] Add Spring Modulith with a module-structure test so package boundaries are enforced.

## 2. Local services
- [ ] `compose.yaml` with Postgres 16 and Keycloak.
- [ ] Realm file in `infra/keycloak/`, imported on startup: five roles
      (PHYSICIAN, MA_NURSE, FRONT_DESK, PATIENT, ADMIN) and one demo user each.

## 3. Database users
- [ ] `ehr_owner` runs Flyway and owns the schema.
- [ ] `ehr_app` is the runtime user; INSERT and SELECT only on `audit_event`.

## 4. First migration (`V1__core.sql`)
- [ ] `patient`, `app_user` (Keycloak subject → role, dept), `care_team_member`,
      `audit_event` with the no-update/no-delete trigger and `prev_hash` chain.
- [ ] `encounter` in its own later migration.
- [ ] Dev-only seed data in a separate location, loaded by the dev profile only.

## 5. Security skeleton
- [ ] Resource server validating Keycloak JWTs; map `realm_access.roles` to authorities.
- [ ] Most tests use `spring-security-test` JWTs; one Testcontainers Keycloak test covers
      the real token path.

## 6. Core slice
- [ ] `GET /api/patients/{id}` guarded by `AccessPolicy` (role + care team) and `@PhiAccess`.
- [ ] Audit writes for denied access use a separate transaction (`REQUIRES_NEW`).
- [ ] Serialize audit inserts (e.g. Postgres advisory lock) so the hash chain stays linear.
- [ ] Tests: care-team physician allowed, other physician denied, both audited.

## 7. CI
- [ ] GitHub Actions running `./gradlew test` on every push.

## 8. Frontend
- [ ] Scaffold `web/` with Vite + React + TypeScript + Tailwind + TanStack Query.
- [ ] Keycloak login, then the Storyboard sidebar reading `GET /api/patients/{id}`.
