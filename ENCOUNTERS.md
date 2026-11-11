# Encounters

Scope: encounter creation, vitals, per-author SOAP notes, Sign Visit, and an encounter snapshot.
The UI is the Encounters activity tab in the chart (`web/src/pages/Encounters.tsx`); a Chart Review
tab that merges encounters with labs and notes comes later.

## Design

- **Routes nest under the patient**: `/api/patients/{patientId}/encounters/...`, so the existing
  `@PhiAccess` aspect runs the care-team check and audit. Every query matches both `patient_id` and
  the encounter id, so another patient's encounter is never reachable through a care-team
  patient's path (404).
- **Access**: both clinical roles can do every encounter action, so the check stays role plus care
  team (`AccessPolicy.canAccessChart`). A per-action matrix is added with the first role-limited
  action (orders, diagnoses).
- **Audit resource**: `@PhiAccess(resourceParam = "encounterId")` records the encounter id as
  `resource_id` for the snapshot, vitals, notes, and signing. Creating an encounter records the
  patient id, since the encounter id does not exist yet.
- **Per-author notes**: each clinician has one SOAP note per encounter (unique on encounter and
  author). The note type follows the author's role: `PROGRESS` for physicians, `ROOMING` for MAs
  and nurses. Only the author edits or signs their note.
- **Optimistic locking**: `note.version` starts at 1 and the snapshot returns it.
  - A `PUT` without a version creates the note; 409 if the caller already has one.
  - A `PUT` with a version updates the note only if the version matches; otherwise 409, and the
    response names the current version.
- **Sign Visit**: allowed for either role once every note on the encounter is signed (409
  otherwise). It sets `SIGNED`, `signed_at`, and `signed_by`. After that, any vitals write, note
  write, or second sign returns 409. Every write first locks the encounter row
  (`select ... for update`), so a sign cannot land between the check and the write.
- **Vitals**: `observation` rows with `category = 'vital-signs'` and `encounter_id` set, using
  LOINC codes and UCUM units from the FHIR R4 vital-signs profile. They are entered in US units.
  The server computes BMI (`703 × lb / in²`) when weight and height are both present. Readings are
  never edited; a correction is a new set.
- **Validation is audited**: request bodies are validated inside the method, after the access
  check. A caller without access gets 403 `DENIED`; a caller with access and a bad payload gets
  400 `ERROR`. Error bodies are RFC 9457 problem details, so the 400 or 409 reason reaches the
  client.

## Routes

| Route | Audit (action, resource_type) |
| --- | --- |
| `GET /encounters` (newest first) | READ, Encounter |
| `POST /encounters` `{providerId, type, reason?, startAt?}` → 201 | CREATE, Encounter |
| `GET /encounters/{id}`: header, vitals, notes | READ, Encounter |
| `POST /encounters/{id}/vitals` → 201 | CREATE, Observation |
| `PUT /encounters/{id}/note` `{subjective?, objective?, assessment?, plan?, version?}` | UPDATE, DocumentReference |
| `POST /encounters/{id}/note/sign` | SIGN, DocumentReference |
| `POST /encounters/{id}/sign` | SIGN, Encounter |

The provider must be on the patient's care team (400 if not). The encounter's department comes from
the provider's `app_user` row.

## Tests

`EncounterTests` (Testcontainers) covers:

- creation allowed, denied, and rejected for a provider off the care team;
- vitals with computed BMI;
- invalid vitals audited as `ERROR`;
- access checked before validation;
- note create, update, stale version, and duplicate create;
- per-author notes;
- signed notes locked;
- Sign Visit gated on signed notes;
- every write rejected after signing;
- the snapshot as a single audit row;
- cross-patient isolation;
- list order;
- unauthenticated requests.

`DevSeedTests` checks the seeded vitals and notes.

## Known limits

- A body that isn't JSON at all, or a path id that isn't a UUID, fails before the controller runs
  and is not audited. Covering it means moving the check and audit to a `HandlerInterceptor`;
  declined for now.
