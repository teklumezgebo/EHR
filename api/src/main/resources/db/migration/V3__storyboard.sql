-- Problem list, allergies, and coverage shown in the Storyboard sidebar.

-- Payer and plan as displayed, e.g. 'Medicare Part B'.
alter table patient add column coverage text;

create table problem (
    id          uuid primary key default gen_random_uuid(),
    patient_id  uuid not null references patient (id),
    icd10       text not null,
    description text not null,
    onset       date,
    -- FHIR Condition.clinicalStatus
    status      text not null default 'active'
        check (status in ('active', 'recurrence', 'relapse', 'inactive', 'remission', 'resolved'))
);

create index problem_patient_idx on problem (patient_id);

create table allergy (
    id         uuid primary key default gen_random_uuid(),
    patient_id uuid not null references patient (id),
    substance  text not null,
    reaction   text,
    -- FHIR AllergyIntolerance.reaction.severity
    severity   text check (severity in ('mild', 'moderate', 'severe'))
);

create index allergy_patient_idx on allergy (patient_id);
