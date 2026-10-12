create table encounter (
    id          uuid primary key default gen_random_uuid(),
    patient_id  uuid        not null references patient (id),
    provider_id uuid        not null references app_user (id),
    department  text        not null,
    type        text        not null,
    reason      text,
    status      text        not null default 'OPEN' check (status in ('OPEN', 'SIGNED')),
    start_at    timestamptz not null,
    signed_at   timestamptz,
    check ((status = 'SIGNED') = (signed_at is not null))
);

create index encounter_patient_idx on encounter (patient_id, start_at desc);
create index encounter_provider_idx on encounter (provider_id);
