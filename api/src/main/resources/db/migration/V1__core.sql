-- Core identity, access, and audit tables. Roles are not stored here: the Keycloak token is the
-- single source of truth for role, and app_user only adds what the token does not carry.

create table app_user (
    id           uuid primary key, -- Keycloak subject (the token's sub claim)
    display_name text not null,
    department   text
);

create table patient (
    id          uuid primary key default gen_random_uuid(),
    mrn         text not null unique,
    given_name  text not null,
    family_name text not null,
    birth_date  date not null,
    sex         text not null check (sex in ('male', 'female', 'other', 'unknown')), -- FHIR AdministrativeGender
    language    text,
    code_status text,
    photo_url   text,
    pcp_id      uuid references app_user (id)
);

create index patient_pcp_idx on patient (pcp_id);

-- Rows are ended rather than deleted, so the history of who was on a care team is kept.
create table care_team_member (
    patient_id uuid        not null references patient (id),
    user_id    uuid        not null references app_user (id),
    added_at   timestamptz not null default now(),
    ended_at   timestamptz,
    primary key (patient_id, user_id)
);

create index care_team_member_user_idx on care_team_member (user_id);

-- Append-only PHI access log. Each row's hash covers its contents and the previous row's hash,
-- so any edit made outside the app (by the schema owner) breaks the chain from that row on.
create sequence audit_event_id_seq;

create table audit_event (
    id            bigint primary key,
    at            timestamptz not null,
    user_id       uuid        not null, -- Keycloak subject; no FK, so attempts by unknown users are still recorded
    action        text        not null,
    outcome       text        not null check (outcome in ('SUCCESS', 'DENIED')),
    resource_type text        not null,
    resource_id   text,
    patient_id    uuid, -- no FK, so probes for nonexistent patients are still recorded
    ip            inet,
    prev_hash     bytea,
    hash          bytea       not null
);

alter sequence audit_event_id_seq owned by audit_event.id;

create index audit_event_patient_idx on audit_event (patient_id, at);
create index audit_event_user_idx on audit_event (user_id, at);

-- Canonical hash of one row. jsonb output is deterministic, and the timestamp is hashed as epoch
-- microseconds so the result does not depend on the session time zone.
create function audit_event_hash(prev bytea, e audit_event) returns bytea
    language sql immutable
as $$
select sha256(coalesce(prev, ''::bytea) || convert_to(jsonb_build_object(
    'id', e.id,
    'at', (extract(epoch from e.at) * 1000000)::bigint,
    'user_id', e.user_id,
    'action', e.action,
    'outcome', e.outcome,
    'resource_type', e.resource_type,
    'resource_id', e.resource_id,
    'patient_id', e.patient_id,
    'ip', host(e.ip)
)::text, 'UTF8'))
$$;

-- Assigns id, timestamp, and hashes under a global lock, so the chain stays linear under
-- concurrent inserts and ids have no gaps except from rolled-back transactions. Any values the
-- caller supplies for these columns are overwritten.
-- Runs as the owner so ehr_app needs no access to the sequence.
create function audit_event_chain() returns trigger
    language plpgsql security definer set search_path = pg_catalog, public
as $$
begin
    -- A repeatable-read snapshot would not see the row committed by the previous lock holder.
    if current_setting('transaction_isolation') <> 'read committed' then
        raise exception 'audit_event inserts require READ COMMITTED isolation';
    end if;

    perform pg_advisory_xact_lock('audit_event'::regclass::oid::bigint);

    new.id := nextval('audit_event_id_seq');
    new.at := clock_timestamp();
    select hash into new.prev_hash from audit_event order by id desc limit 1;
    new.hash := audit_event_hash(new.prev_hash, new);
    return new;
end
$$;

create trigger audit_event_chain
    before insert on audit_event
    for each row execute function audit_event_chain();

-- Grants already deny ehr_app; this also stops the schema owner short of disabling the trigger.
create function audit_event_reject_change() returns trigger
    language plpgsql
as $$
begin
    raise exception 'audit_event is append-only';
end
$$;

create trigger audit_event_no_update_delete
    before update or delete on audit_event
    for each row execute function audit_event_reject_change();

create trigger audit_event_no_truncate
    before truncate on audit_event
    for each statement execute function audit_event_reject_change();

revoke update on audit_event from ehr_app;
revoke all on sequence audit_event_id_seq from ehr_app;
