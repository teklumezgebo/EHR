-- Lab results and, later, vitals. Each row is one component result (FHIR Observation); panel
-- groups components drawn together, e.g. the sodium in a BMP.
create table observation (
    id           uuid primary key default gen_random_uuid(),
    patient_id   uuid        not null references patient (id),
    encounter_id uuid references encounter (id),
    -- FHIR Observation.category
    category     text        not null check (category in ('laboratory', 'vital-signs')),
    loinc        text        not null,
    display      text        not null,
    panel        text,
    value        numeric     not null,
    unit         text        not null,
    -- Either bound may be absent, e.g. LDL has only an upper limit.
    ref_low      numeric,
    ref_high     numeric,
    taken_at     timestamptz not null
);

create index observation_patient_idx on observation (patient_id, category, loinc, taken_at desc);
