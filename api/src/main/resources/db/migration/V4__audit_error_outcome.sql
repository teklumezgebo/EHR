-- ERROR marks an authorized access that failed partway, so the attempt is still on record.
alter table audit_event drop constraint audit_event_outcome_check;
alter table audit_event add constraint audit_event_outcome_check
    check (outcome in ('SUCCESS', 'DENIED', 'ERROR'));
