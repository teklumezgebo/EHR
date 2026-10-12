-- Synthetic demo data. User ids match the demo users in infra/keycloak/ehr-realm.json.
-- Flyway reruns this file whenever it changes; existing rows are left alone, so edits made
-- through the app survive a reseed.

insert into app_user (id, display_name, department) values
    ('6f1c2a10-0001-4c8e-9a51-000000000001', 'Maya Chen, MD', 'Family Medicine'),
    ('6f1c2a10-0002-4c8e-9a51-000000000002', 'Luis Ortega, MA', 'Family Medicine'),
    ('6f1c2a10-0003-4c8e-9a51-000000000003', 'Priya Nair', 'Compliance'),
    -- No login: PCP for the patient outside Dr. Chen's care team.
    ('6f1c2a10-0101-4c8e-9a51-000000000101', 'Omar Haddad, MD', 'Endocrinology')
on conflict do nothing;

insert into patient (id, mrn, given_name, family_name, birth_date, sex, language, code_status, coverage, pcp_id) values
    ('c0ffee00-0001-4a7b-8000-000000000001', 'E1000001', 'Sam', 'Rivera', '1971-04-12', 'male', 'English', 'Full Code', 'Commercial PPO', '6f1c2a10-0001-4c8e-9a51-000000000001'),
    ('c0ffee00-0002-4a7b-8000-000000000002', 'E1000002', 'Rosa', 'Delgado', '1958-11-03', 'female', 'Spanish', 'DNR', 'Medicare Part B', '6f1c2a10-0001-4c8e-9a51-000000000001'),
    ('c0ffee00-0003-4a7b-8000-000000000003', 'E1000003', 'Kwame', 'Asante', '1989-07-21', 'male', 'English', 'Full Code', 'Medicaid', '6f1c2a10-0001-4c8e-9a51-000000000001'),
    ('c0ffee00-0004-4a7b-8000-000000000004', 'E1000004', 'Helen', 'Park', '1945-02-28', 'female', 'Korean', 'Full Code', 'Medicare Advantage HMO', '6f1c2a10-0101-4c8e-9a51-000000000101')
-- Fills coverage on rows seeded before the column existed without overwriting an edited value.
on conflict (id) do update set coverage = excluded.coverage where patient.coverage is null;

insert into care_team_member (patient_id, user_id) values
    ('c0ffee00-0001-4a7b-8000-000000000001', '6f1c2a10-0001-4c8e-9a51-000000000001'),
    ('c0ffee00-0001-4a7b-8000-000000000001', '6f1c2a10-0002-4c8e-9a51-000000000002'),
    ('c0ffee00-0002-4a7b-8000-000000000002', '6f1c2a10-0001-4c8e-9a51-000000000001'),
    ('c0ffee00-0002-4a7b-8000-000000000002', '6f1c2a10-0002-4c8e-9a51-000000000002'),
    ('c0ffee00-0003-4a7b-8000-000000000003', '6f1c2a10-0001-4c8e-9a51-000000000001'),
    ('c0ffee00-0004-4a7b-8000-000000000004', '6f1c2a10-0101-4c8e-9a51-000000000101')
on conflict do nothing;

insert into encounter (id, patient_id, provider_id, department, type, reason, status, start_at, signed_at) values
    ('e0000000-0001-4d00-8000-000000000001', 'c0ffee00-0001-4a7b-8000-000000000001', '6f1c2a10-0001-4c8e-9a51-000000000001',
     'Family Medicine', 'Office Visit', 'Diabetes Follow Up', 'SIGNED', '2026-06-02 09:00-04', '2026-06-02 09:45-04'),
    ('e0000000-0002-4d00-8000-000000000002', 'c0ffee00-0001-4a7b-8000-000000000001', '6f1c2a10-0001-4c8e-9a51-000000000001',
     'Family Medicine', 'Office Visit', 'Diabetes Follow Up', 'OPEN', '2026-09-02 09:00-04', null),
    ('e0000000-0003-4d00-8000-000000000003', 'c0ffee00-0002-4a7b-8000-000000000002', '6f1c2a10-0001-4c8e-9a51-000000000001',
     'Family Medicine', 'Office Visit', 'Hypertension Check', 'SIGNED', '2026-08-14 14:30-04', '2026-08-14 15:10-04'),
    ('e0000000-0004-4d00-8000-000000000004', 'c0ffee00-0004-4a7b-8000-000000000004', '6f1c2a10-0101-4c8e-9a51-000000000101',
     'Endocrinology', 'Office Visit', 'Thyroid Nodule Follow Up', 'SIGNED', '2026-07-09 10:00-04', '2026-07-09 10:40-04')
on conflict do nothing;

insert into problem (id, patient_id, icd10, description, onset, status) values
    ('a0000000-0001-4b00-8000-000000000001', 'c0ffee00-0001-4a7b-8000-000000000001', 'E11.9', 'Type 2 diabetes mellitus without complications', '2018-03-15', 'active'),
    ('a0000000-0002-4b00-8000-000000000002', 'c0ffee00-0001-4a7b-8000-000000000001', 'I10', 'Essential hypertension', '2016-09-01', 'active'),
    ('a0000000-0003-4b00-8000-000000000003', 'c0ffee00-0001-4a7b-8000-000000000001', 'E78.5', 'Hyperlipidemia, unspecified', '2019-01-22', 'active'),
    ('a0000000-0004-4b00-8000-000000000004', 'c0ffee00-0002-4a7b-8000-000000000002', 'I10', 'Essential hypertension', '2005-06-10', 'active'),
    ('a0000000-0005-4b00-8000-000000000005', 'c0ffee00-0002-4a7b-8000-000000000002', 'N18.30', 'Chronic kidney disease, stage 3 unspecified', '2021-11-04', 'active'),
    ('a0000000-0006-4b00-8000-000000000006', 'c0ffee00-0003-4a7b-8000-000000000003', 'J45.20', 'Mild intermittent asthma, uncomplicated', '2002-04-01', 'active'),
    ('a0000000-0007-4b00-8000-000000000007', 'c0ffee00-0003-4a7b-8000-000000000003', 'S93.401A', 'Sprain of right ankle, initial encounter', '2025-08-19', 'resolved'),
    ('a0000000-0008-4b00-8000-000000000008', 'c0ffee00-0004-4a7b-8000-000000000004', 'E04.1', 'Nontoxic single thyroid nodule', '2024-02-12', 'active'),
    ('a0000000-0009-4b00-8000-000000000009', 'c0ffee00-0004-4a7b-8000-000000000004', 'M81.0', 'Age-related osteoporosis without current pathological fracture', '2015-10-30', 'active')
on conflict do nothing;

-- Kwame Asante has no allergies recorded.
insert into allergy (id, patient_id, substance, reaction, severity) values
    ('b0000000-0001-4b00-8000-000000000001', 'c0ffee00-0001-4a7b-8000-000000000001', 'Penicillin', 'Hives', 'moderate'),
    ('b0000000-0002-4b00-8000-000000000002', 'c0ffee00-0002-4a7b-8000-000000000002', 'Lisinopril', 'Angioedema', 'severe'),
    ('b0000000-0003-4b00-8000-000000000003', 'c0ffee00-0002-4a7b-8000-000000000002', 'Sulfonamide antibiotics', 'Rash', 'mild'),
    ('b0000000-0004-4b00-8000-000000000004', 'c0ffee00-0004-4a7b-8000-000000000004', 'Codeine', 'Nausea', 'mild')
on conflict do nothing;
