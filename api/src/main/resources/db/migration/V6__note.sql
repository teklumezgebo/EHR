-- Visit notes and the encounter signer. Each clinician keeps one SOAP note per encounter and signs
-- it themselves; Sign Visit then locks the encounter once every note on it is signed.

alter table encounter add column signed_by uuid references app_user (id);

-- Encounters signed before this column existed were signed by their provider.
update encounter set signed_by = provider_id where status = 'SIGNED';

alter table encounter drop constraint encounter_check;
alter table encounter add constraint encounter_signed_check
    check ((status = 'SIGNED') = (signed_at is not null and signed_by is not null));

create table note (
    id           uuid primary key default gen_random_uuid(),
    encounter_id uuid        not null references encounter (id),
    author_id    uuid        not null references app_user (id),
    -- PROGRESS is the provider's visit note; ROOMING is the MA or nurse intake note.
    type         text        not null check (type in ('PROGRESS', 'ROOMING')),
    subjective   text        not null default '',
    objective    text        not null default '',
    assessment   text        not null default '',
    plan         text        not null default '',
    -- Incremented on every save; a save must name the version it was edited from.
    version      int         not null default 1,
    updated_at   timestamptz not null default now(),
    signed_at    timestamptz,
    unique (encounter_id, author_id)
);
