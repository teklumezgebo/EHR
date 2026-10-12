# EHR

An electronic health record (EHR) modeled on Epic Hyperspace. A Spring Boot backend serves patient
data through a FHIR R4 API, and a React frontend gives clinicians an Epic-style workspace. See
`SPEC.md` for the design and `STEPS.md` for build progress.

## How it fits together

1. **Keycloak** handles login. It knows who each user is and what role they have (physician, nurse,
   or auditor).
2. The **web app** (`web/`) sends the user to Keycloak to sign in, then calls the API with the
   token it gets back.
3. The **API** (`api/`) checks the token, decides whether this user may see this patient, records
   the attempt in the audit log, and returns the data.
4. **Postgres** stores everything. The audit log table can only be added to, never edited or
   deleted.

## Folders and files

### Top level

| Path | What it is |
| --- | --- |
| `api/` | The backend server (Java 21, Spring Boot 4). |
| `web/` | The frontend the user clicks around in (React, TypeScript, Vite). |
| `infra/` | Setup files for Keycloak and Postgres used during local development. |
| `.devcontainer/` | Defines the dev container: a ready-made machine with Java, Node, and other tools installed. |
| `.github/workflows/` | GitHub Actions. `devcontainer.yml` builds the dev container image and publishes it. |
| `compose.yaml` | Starts Postgres and Keycloak in Docker. |
| `dev.sh` | One command that starts everything: Docker services, the API, and the web app. |
| `SPEC.md` | What the product should do and how it is designed. Updated as the project evolves. |
| `STEPS.md` | The build order, as a checklist. |
| `CLAUDE.md` | Project instructions for Claude Code. |

### `api/` — the backend

| Path | What it is |
| --- | --- |
| `build.gradle.kts` | Lists the libraries the backend uses and how to build it. |
| `settings.gradle.kts` | Names the Gradle project. |
| `gradlew`, `gradlew.bat` | Run Gradle without installing it (`./gradlew test`, `./gradlew bootRun`). |

Java code lives in `api/src/main/java/com/teklumezgebo/ehr/`. Each folder is a module that owns
one area of the app.

| Path | What it is |
| --- | --- |
| `EhrApplication.java` | Starts the server. |
| `access/AccessPolicy.java` | Decides who may open a patient's chart: the user must be a physician or nurse on that patient's active care team. |
| `audit/PhiAccess.java` | A tag (`@PhiAccess`) placed on any method that touches patient data. Tagged methods are checked and logged automatically. |
| `audit/PhiAccessAspect.java` | Runs around every `@PhiAccess` method: asks `AccessPolicy` for permission, then writes `SUCCESS` or `DENIED` to the audit log. |
| `audit/AuditLog.java` | Writes rows to the `audit_event` table. Each write is saved even if the request fails. |
| `audit/AuditEventController.java` | `GET /api/audit-events`: lets auditors read the audit log. |
| `patient/PatientController.java` | `GET /api/patients/{id}`: returns the opened chart (Storyboard summary, problem list, allergies) in one response. |
| `clinical/ClinicalLists.java` | Queries for the problem list and allergies. Not an endpoint; used by `PatientController`. |

Configuration and database files live in `api/src/main/resources/`.

| Path | What it is |
| --- | --- |
| `application.properties` | Main settings: database connection, Flyway migrations, login token checks. |
| `application-dev.properties` | Extra settings for local development. Loads the demo patients. |
| `db/migration/V1__core.sql` | Creates the core tables: users, patients, care teams, and the audit log. |
| `db/migration/V2__encounter.sql` | Creates the encounters (visits) table. |
| `db/migration/afterMigrate.sql` | Runs after each migration and keeps the app's database user from touching Flyway's history table. |
| `db/seed/R__dev_seed.sql` | Fake demo patients for local development. Never loaded in production. |

Tests live in `api/src/test/java/com/teklumezgebo/ehr/`. They run against a real Postgres in Docker
(Testcontainers).

| Path | What it checks |
| --- | --- |
| `PatientAccessTests.java` | Care-team members can open a chart, others are blocked, and every attempt is logged. |
| `AuditEventAccessTests.java` | Only auditors can read the audit log, every attempt is logged, and auditors' own reads are hidden from their view. |
| `AuditEventTableTests.java` | Audit rows cannot be edited or deleted, and each row is chained to the one before it so tampering shows up. |
| `DatabaseRolesTests.java` | The app's database user cannot create tables, delete rows, or rewrite history. |
| `KeycloakTokenTests.java` | A real Keycloak login token is accepted and its role is read correctly. |
| `DevSeedTests.java` | The demo data still loads against the current database schema. |
| `ModularityTests.java` | Modules only use each other through their public parts, and never depend on each other in a loop. |
| `EhrApplicationTests.java` | The app starts up. |
| `TestcontainersConfiguration.java` | Starts the Postgres container the tests use. |
| `TestEhrApplication.java` | Runs the app locally against a throwaway Postgres container. |

### `web/` — the frontend

| Path | What it is |
| --- | --- |
| `index.html` | The single HTML page the app loads into. |
| `package.json`, `package-lock.json` | The frontend's libraries and scripts (`npm run dev`). |
| `vite.config.ts` | Dev server on port 5173. Forwards `/api` requests to the backend. |
| `tsconfig*.json` | TypeScript settings. |
| `.oxlintrc.json` | Lint rules. |
| `src/main.tsx` | Starts the React app. |
| `src/auth.ts` | Keycloak login, and a `fetch` helper that attaches the login token to API calls. |
| `src/App.tsx` | The workspace shell: top bar, tabs, and which page each tab shows. |
| `src/pages/Lookup.tsx` | Patient lookup: the seeded patient list and open-by-ID form. |
| `src/pages/Chart.tsx` | A patient chart: Storyboard sidebar and the SnapShot (problem list and allergies). |
| `src/pages/AuditLog.tsx` | The audit log table for auditors, with paging. |
| `src/index.css` | Styles. |

### `infra/` — local services

| Path | What it is |
| --- | --- |
| `keycloak/ehr-realm.json` | Keycloak setup: roles, the web app's login client, and the demo users. |
| `postgres/init/01-roles.sh` | Creates two database users: `ehr_owner` builds the tables, and `ehr_app` (used by the running app) can only read and write data. |

### `.devcontainer/` — dev environment

| Path | What it is |
| --- | --- |
| `devcontainer.json` | Opens the project in the prebuilt dev image from GitHub's container registry. |
| `build/Dockerfile` | How the dev image is built: JDK 21, Node 24, Terraform, GitHub CLI, psql. |
| `build/devcontainer.json` | Builds the dev image from the Dockerfile instead of downloading it. |

## Running locally

```sh
./dev.sh   # Postgres :5432, Keycloak :8081 (admin/admin), API :8080, web http://localhost:5173
```

`dev.sh` starts the compose services, runs the API with the dev profile (migrates and seeds the
database), and starts the web dev server. Ctrl+C stops the API and web server; `docker compose down`
stops the containers. Run tests with `cd api && ./gradlew test`.

### Demo logins

Sign in at http://localhost:5173 with any of these Keycloak users (defined in `infra/keycloak/ehr-realm.json`).

| Username | Password | Name | Role | What they can do |
| --- | --- | --- | --- | --- |
| `physician` | `demo` | Maya Chen, MD | PHYSICIAN | Open charts for Rivera, Delgado, and Asante. Opening Park is denied and audited. |
| `nurse` | `demo` | Luis Ortega, MA | MA_NURSE | Open charts for Rivera and Delgado only. |
| `auditor` | `demo` | Priya Nair | AUDITOR | Read the audit log. Cannot open charts. |

Keycloak admin console: http://localhost:8081, username `admin`, password `admin`.

Keycloak imports the realm only once, so run `docker compose down` and `up` again to load new users or roles.

## API

All `/api/**` routes require a Keycloak access token for the `ehr` realm (`Authorization: Bearer …`).
Set `API_URL` (web) when the backend is not on `http://localhost:8080`.
Set `KEYCLOAK_URL` when the API cannot reach Keycloak at `http://localhost:8081`.

| Route | Access | Audited |
| --- | --- | --- |
| `GET /api/patients/{id}` | PHYSICIAN or MA_NURSE on the patient's active care team; otherwise 403 | Every attempt, as `READ Patient` with `SUCCESS` or `DENIED` |
| `GET /api/audit-events?before={id}` | AUDITOR only; otherwise 403. Newest first, 100 per page. Successful audit log reads are left out of the results | Every attempt, as `READ AuditEvent` with `SUCCESS` or `DENIED` |
