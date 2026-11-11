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

insert into encounter (id, patient_id, provider_id, department, type, reason, status, start_at, signed_at, signed_by) values
    ('e0000000-0001-4d00-8000-000000000001', 'c0ffee00-0001-4a7b-8000-000000000001', '6f1c2a10-0001-4c8e-9a51-000000000001',
     'Family Medicine', 'Office Visit', 'Diabetes Follow Up', 'SIGNED', '2026-06-02 09:00-04', '2026-06-02 09:45-04', '6f1c2a10-0001-4c8e-9a51-000000000001'),
    ('e0000000-0002-4d00-8000-000000000002', 'c0ffee00-0001-4a7b-8000-000000000001', '6f1c2a10-0001-4c8e-9a51-000000000001',
     'Family Medicine', 'Office Visit', 'Diabetes Follow Up', 'OPEN', '2026-09-02 09:00-04', null, null),
    ('e0000000-0003-4d00-8000-000000000003', 'c0ffee00-0002-4a7b-8000-000000000002', '6f1c2a10-0001-4c8e-9a51-000000000001',
     'Family Medicine', 'Office Visit', 'Hypertension Check', 'SIGNED', '2026-08-14 14:30-04', '2026-08-14 15:10-04', '6f1c2a10-0001-4c8e-9a51-000000000001'),
    ('e0000000-0004-4d00-8000-000000000004', 'c0ffee00-0004-4a7b-8000-000000000004', '6f1c2a10-0101-4c8e-9a51-000000000101',
     'Endocrinology', 'Office Visit', 'Thyroid Nodule Follow Up', 'SIGNED', '2026-07-09 10:00-04', '2026-07-09 10:40-04', '6f1c2a10-0101-4c8e-9a51-000000000101')
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

-- Lab results. Reference ranges follow common commercial lab ranges; sex is set where the range
-- differs by sex. Ids are derived from the draw key and LOINC code, so a reseed adds no duplicates.
with code (loinc, sex, display, unit, ref_low, ref_high) as (values
    ('4548-4',  null::text, 'Hemoglobin A1c', '%', null::numeric, 5.6::numeric),
    ('3016-3',  null, 'TSH', 'mIU/L', 0.45, 4.5),
    ('3024-7',  null, 'Free T4', 'ng/dL', 0.82, 1.77),
    ('62292-8', null, 'Vitamin D, 25-Hydroxy', 'ng/mL', 30, 100),
    ('9318-7',  null, 'Microalbumin/Creatinine Ratio, Urine', 'mg/g', null, 29),
    ('2951-2',  null, 'Sodium', 'mmol/L', 134, 144),
    ('2823-3',  null, 'Potassium', 'mmol/L', 3.5, 5.2),
    ('2075-0',  null, 'Chloride', 'mmol/L', 96, 106),
    ('2028-9',  null, 'Carbon Dioxide', 'mmol/L', 20, 29),
    ('3094-0',  null, 'BUN', 'mg/dL', 6, 24),
    ('2160-0',  'male', 'Creatinine', 'mg/dL', 0.76, 1.27),
    ('2160-0',  'female', 'Creatinine', 'mg/dL', 0.57, 1.00),
    ('98979-8', null, 'eGFR', 'mL/min/1.73m2', 60, null),
    ('2345-7',  null, 'Glucose', 'mg/dL', 70, 99),
    ('17861-6', null, 'Calcium', 'mg/dL', 8.7, 10.2),
    ('2885-2',  null, 'Total Protein', 'g/dL', 6.0, 8.5),
    ('1751-7',  null, 'Albumin', 'g/dL', 3.8, 4.9),
    ('1975-2',  null, 'Bilirubin, Total', 'mg/dL', 0, 1.2),
    ('6768-6',  null, 'Alkaline Phosphatase', 'IU/L', 44, 121),
    ('1920-8',  null, 'AST', 'IU/L', 0, 40),
    ('1742-6',  null, 'ALT', 'IU/L', 0, 44),
    ('2093-3',  null, 'Cholesterol, Total', 'mg/dL', 100, 199),
    ('2571-8',  null, 'Triglycerides', 'mg/dL', 0, 149),
    ('2085-9',  null, 'HDL Cholesterol', 'mg/dL', 40, null),
    ('13457-7', null, 'LDL Cholesterol, Calculated', 'mg/dL', 0, 99),
    ('6690-2',  null, 'WBC', 'x10^3/uL', 3.4, 10.8),
    ('718-7',   'male', 'Hemoglobin', 'g/dL', 13.0, 17.7),
    ('718-7',   'female', 'Hemoglobin', 'g/dL', 11.1, 15.9),
    ('4544-3',  'male', 'Hematocrit', '%', 37.5, 51.0),
    ('4544-3',  'female', 'Hematocrit', '%', 34.0, 46.6),
    ('777-3',   null, 'Platelets', 'x10^3/uL', 150, 450)
),
draw (key, patient_id, panel, taken_at) as (values
    ('sam-0528',        'c0ffee00-0001-4a7b-8000-000000000001'::uuid, null::text, '2026-05-28 08:15-04'::timestamptz),
    ('sam-0528-bmp',    'c0ffee00-0001-4a7b-8000-000000000001', 'BMP', '2026-05-28 08:15-04'),
    ('sam-0826',        'c0ffee00-0001-4a7b-8000-000000000001', null, '2026-08-26 08:05-04'),
    ('sam-0826-cmp',    'c0ffee00-0001-4a7b-8000-000000000001', 'CMP', '2026-08-26 08:05-04'),
    ('sam-0826-lipid',  'c0ffee00-0001-4a7b-8000-000000000001', 'Lipid Panel', '2026-08-26 08:05-04'),
    ('rosa-0210',       'c0ffee00-0002-4a7b-8000-000000000002', null, '2026-02-10 09:30-05'),
    ('rosa-0210-bmp',   'c0ffee00-0002-4a7b-8000-000000000002', 'BMP', '2026-02-10 09:30-05'),
    ('rosa-0210-lipid', 'c0ffee00-0002-4a7b-8000-000000000002', 'Lipid Panel', '2026-02-10 09:30-05'),
    ('rosa-0810',       'c0ffee00-0002-4a7b-8000-000000000002', null, '2026-08-10 10:00-04'),
    ('rosa-0810-bmp',   'c0ffee00-0002-4a7b-8000-000000000002', 'BMP', '2026-08-10 10:00-04'),
    ('rosa-0810-cbc',   'c0ffee00-0002-4a7b-8000-000000000002', 'CBC', '2026-08-10 10:00-04'),
    ('kwame-0915-bmp',  'c0ffee00-0003-4a7b-8000-000000000003', 'BMP', '2025-09-15 11:20-04'),
    ('kwame-0915-cbc',  'c0ffee00-0003-4a7b-8000-000000000003', 'CBC', '2025-09-15 11:20-04'),
    ('helen-0105',      'c0ffee00-0004-4a7b-8000-000000000004', null, '2026-01-05 08:45-05'),
    ('helen-0702',      'c0ffee00-0004-4a7b-8000-000000000004', null, '2026-07-02 08:30-04'),
    ('helen-0702-cmp',  'c0ffee00-0004-4a7b-8000-000000000004', 'CMP', '2026-07-02 08:30-04')
),
result (draw, loinc, value) as (values
    -- Sam Rivera: diabetes improving but above goal, LDL and triglycerides high.
    ('sam-0528', '4548-4', 8.1::numeric), ('sam-0528', '3016-3', 2.1),
    ('sam-0528-bmp', '2951-2', 139), ('sam-0528-bmp', '2823-3', 4.4), ('sam-0528-bmp', '2075-0', 102),
    ('sam-0528-bmp', '2028-9', 25), ('sam-0528-bmp', '3094-0', 16), ('sam-0528-bmp', '2160-0', 0.95),
    ('sam-0528-bmp', '98979-8', 92), ('sam-0528-bmp', '2345-7', 168), ('sam-0528-bmp', '17861-6', 9.4),
    ('sam-0826', '4548-4', 7.4), ('sam-0826', '9318-7', 42),
    ('sam-0826-cmp', '2951-2', 138), ('sam-0826-cmp', '2823-3', 4.6), ('sam-0826-cmp', '2075-0', 101),
    ('sam-0826-cmp', '2028-9', 24), ('sam-0826-cmp', '3094-0', 18), ('sam-0826-cmp', '2160-0', 0.98),
    ('sam-0826-cmp', '98979-8', 90), ('sam-0826-cmp', '2345-7', 152), ('sam-0826-cmp', '17861-6', 9.5),
    ('sam-0826-cmp', '2885-2', 7.1), ('sam-0826-cmp', '1751-7', 4.3), ('sam-0826-cmp', '1975-2', 0.6),
    ('sam-0826-cmp', '6768-6', 82), ('sam-0826-cmp', '1920-8', 31), ('sam-0826-cmp', '1742-6', 47),
    ('sam-0826-lipid', '2093-3', 198), ('sam-0826-lipid', '2571-8', 210), ('sam-0826-lipid', '2085-9', 38),
    ('sam-0826-lipid', '13457-7', 118),
    -- Rosa Delgado: CKD 3b with rising potassium, anemia, low vitamin D.
    ('rosa-0210', '62292-8', 16), ('rosa-0210', '9318-7', 165),
    ('rosa-0210-bmp', '2951-2', 140), ('rosa-0210-bmp', '2823-3', 5.1), ('rosa-0210-bmp', '2075-0', 104),
    ('rosa-0210-bmp', '2028-9', 22), ('rosa-0210-bmp', '3094-0', 28), ('rosa-0210-bmp', '2160-0', 1.35),
    ('rosa-0210-bmp', '98979-8', 43), ('rosa-0210-bmp', '2345-7', 96), ('rosa-0210-bmp', '17861-6', 9.1),
    ('rosa-0210-lipid', '2093-3', 182), ('rosa-0210-lipid', '2571-8', 128), ('rosa-0210-lipid', '2085-9', 54),
    ('rosa-0210-lipid', '13457-7', 102),
    ('rosa-0810', '62292-8', 24), ('rosa-0810', '9318-7', 180),
    ('rosa-0810-bmp', '2951-2', 138), ('rosa-0810-bmp', '2823-3', 5.4), ('rosa-0810-bmp', '2075-0', 105),
    ('rosa-0810-bmp', '2028-9', 21), ('rosa-0810-bmp', '3094-0', 31), ('rosa-0810-bmp', '2160-0', 1.42),
    ('rosa-0810-bmp', '98979-8', 40), ('rosa-0810-bmp', '2345-7', 101), ('rosa-0810-bmp', '17861-6', 9.0),
    ('rosa-0810-cbc', '6690-2', 6.2), ('rosa-0810-cbc', '718-7', 10.9), ('rosa-0810-cbc', '4544-3', 33.1),
    ('rosa-0810-cbc', '777-3', 231),
    -- Kwame Asante: normal results.
    ('kwame-0915-bmp', '2951-2', 141), ('kwame-0915-bmp', '2823-3', 4.1), ('kwame-0915-bmp', '2075-0', 103),
    ('kwame-0915-bmp', '2028-9', 26), ('kwame-0915-bmp', '3094-0', 14), ('kwame-0915-bmp', '2160-0', 1.04),
    ('kwame-0915-bmp', '98979-8', 94), ('kwame-0915-bmp', '2345-7', 88), ('kwame-0915-bmp', '17861-6', 9.7),
    ('kwame-0915-cbc', '6690-2', 7.1), ('kwame-0915-cbc', '718-7', 15.2), ('kwame-0915-cbc', '4544-3', 45.0),
    ('kwame-0915-cbc', '777-3', 262),
    -- Helen Park: TSH now suppressed, vitamin D repleted.
    ('helen-0105', '3016-3', 1.10), ('helen-0105', '62292-8', 27),
    ('helen-0702', '3016-3', 0.38), ('helen-0702', '3024-7', 1.52), ('helen-0702', '62292-8', 34),
    ('helen-0702-cmp', '2951-2', 137), ('helen-0702-cmp', '2823-3', 4.3), ('helen-0702-cmp', '2075-0', 100),
    ('helen-0702-cmp', '2028-9', 26), ('helen-0702-cmp', '3094-0', 22), ('helen-0702-cmp', '2160-0', 0.82),
    ('helen-0702-cmp', '98979-8', 68), ('helen-0702-cmp', '2345-7', 94), ('helen-0702-cmp', '17861-6', 9.6),
    ('helen-0702-cmp', '2885-2', 6.8), ('helen-0702-cmp', '1751-7', 4.0), ('helen-0702-cmp', '1975-2', 0.5),
    ('helen-0702-cmp', '6768-6', 98), ('helen-0702-cmp', '1920-8', 24), ('helen-0702-cmp', '1742-6', 19)
)
insert into observation (id, patient_id, category, loinc, display, panel, value, unit, ref_low, ref_high, taken_at)
select md5(r.draw || r.loinc)::uuid, d.patient_id, 'laboratory', r.loinc, c.display, d.panel, r.value, c.unit,
       c.ref_low, c.ref_high, d.taken_at
from result r
join draw d on d.key = r.draw
join patient p on p.id = d.patient_id
join code c on c.loinc = r.loinc and (c.sex is null or c.sex = p.sex)
on conflict do nothing;

-- Rooming vitals for each seeded visit, in the units the API accepts (see EncounterController).
-- Sam Rivera's open 9/2 visit has vitals but no notes yet, so it can be documented and signed.
with vital_code (loinc, display, panel, unit) as (values
    ('8480-6',  'Systolic blood pressure', 'Blood Pressure', 'mm[Hg]'),
    ('8462-4',  'Diastolic blood pressure', 'Blood Pressure', 'mm[Hg]'),
    ('8867-4',  'Heart rate', null, '/min'),
    ('9279-1',  'Respiratory rate', null, '/min'),
    ('8310-5',  'Body temperature', null, '[degF]'),
    ('59408-5', 'Oxygen saturation by pulse oximetry', null, '%'),
    ('29463-7', 'Body weight', null, '[lb_av]'),
    ('8302-2',  'Body height', null, '[in_i]'),
    ('39156-5', 'Body mass index', null, 'kg/m2')
),
vital_set (key, encounter_id, taken_at) as (values
    ('sam-0602',   'e0000000-0001-4d00-8000-000000000001'::uuid, '2026-06-02 09:08-04'::timestamptz),
    ('sam-0902',   'e0000000-0002-4d00-8000-000000000002', '2026-09-02 09:06-04'),
    ('rosa-0814',  'e0000000-0003-4d00-8000-000000000003', '2026-08-14 14:38-04'),
    ('helen-0709', 'e0000000-0004-4d00-8000-000000000004', '2026-07-09 10:07-04')
),
vital (set_key, loinc, value) as (values
    -- BMI is 703 x lb / in^2, matching what the API computes.
    ('sam-0602', '8480-6', 142::numeric), ('sam-0602', '8462-4', 88), ('sam-0602', '8867-4', 78),
    ('sam-0602', '9279-1', 16), ('sam-0602', '8310-5', 98.2), ('sam-0602', '59408-5', 98),
    ('sam-0602', '29463-7', 214), ('sam-0602', '8302-2', 70), ('sam-0602', '39156-5', 30.7),
    ('sam-0902', '8480-6', 134), ('sam-0902', '8462-4', 84), ('sam-0902', '8867-4', 74),
    ('sam-0902', '9279-1', 16), ('sam-0902', '8310-5', 98.4), ('sam-0902', '59408-5', 97),
    ('sam-0902', '29463-7', 208), ('sam-0902', '8302-2', 70), ('sam-0902', '39156-5', 29.8),
    ('rosa-0814', '8480-6', 156), ('rosa-0814', '8462-4', 92), ('rosa-0814', '8867-4', 82),
    ('rosa-0814', '9279-1', 18), ('rosa-0814', '8310-5', 97.9), ('rosa-0814', '59408-5', 96),
    ('rosa-0814', '29463-7', 168), ('rosa-0814', '8302-2', 62), ('rosa-0814', '39156-5', 30.7),
    ('helen-0709', '8480-6', 128), ('helen-0709', '8462-4', 76), ('helen-0709', '8867-4', 88),
    ('helen-0709', '9279-1', 14), ('helen-0709', '8310-5', 98.0), ('helen-0709', '59408-5', 98),
    ('helen-0709', '29463-7', 118), ('helen-0709', '8302-2', 61), ('helen-0709', '39156-5', 22.3)
)
insert into observation (id, patient_id, encounter_id, category, loinc, display, panel, value, unit, taken_at)
select md5(s.key || v.loinc)::uuid, e.patient_id, e.id, 'vital-signs', v.loinc, c.display, c.panel, v.value,
       c.unit, s.taken_at
from vital v
join vital_set s on s.key = v.set_key
join encounter e on e.id = s.encounter_id
join vital_code c on c.loinc = v.loinc
on conflict do nothing;

-- Signed notes for the signed visits: the MA's rooming note and the provider's progress note.
insert into note (id, encounter_id, author_id, type, subjective, objective, assessment, plan, updated_at, signed_at) values
    ('d0000000-0001-4e00-8000-000000000001', 'e0000000-0001-4d00-8000-000000000001', '6f1c2a10-0002-4c8e-9a51-000000000002',
     'ROOMING', 'Here for diabetes follow up. Checks sugars most mornings, 140s-180s. No lows.',
     'Vitals taken. Medications and allergies reviewed with patient.', '', '',
     '2026-06-02 09:12-04', '2026-06-02 09:12-04'),
    ('d0000000-0002-4e00-8000-000000000002', 'e0000000-0001-4d00-8000-000000000001', '6f1c2a10-0001-4c8e-9a51-000000000001',
     'PROGRESS', 'Type 2 diabetes follow up. Taking metformin 1000 mg twice daily. Reports mild fatigue, no polyuria or vision change.',
     'BP 142/88, BMI 30.7. A1c 8.1% (5/28). Feet: intact sensation, no ulcers.',
     'Type 2 diabetes above goal. Hypertension not at goal. Hyperlipidemia.',
     'Start empagliflozin 10 mg daily. Recheck A1c in 3 months. Reinforce diet and walking. Follow up 3 months.',
     '2026-06-02 09:44-04', '2026-06-02 09:44-04'),
    ('d0000000-0003-4e00-8000-000000000003', 'e0000000-0003-4d00-8000-000000000003', '6f1c2a10-0001-4c8e-9a51-000000000001',
     'PROGRESS', 'Hypertension check. Taking amlodipine. Occasional ankle swelling. Interpreter used (Spanish).',
     'BP 156/92, repeat 150/90. Trace bilateral ankle edema. K 5.4, eGFR 40 (8/10).',
     'Hypertension not at goal. CKD stage 3b with rising potassium.',
     'Increase amlodipine to 10 mg. Avoid ACE inhibitors (angioedema history). Low-potassium diet handout. Nephrology referral. Repeat BMP in 4 weeks.',
     '2026-08-14 15:08-04', '2026-08-14 15:08-04'),
    ('d0000000-0004-4e00-8000-000000000004', 'e0000000-0004-4d00-8000-000000000004', '6f1c2a10-0101-4c8e-9a51-000000000101',
     'PROGRESS', 'Thyroid nodule follow up. No neck pain, hoarseness, or trouble swallowing.',
     'Neck: small nontender right thyroid nodule, unchanged. TSH 0.38 (7/2), free T4 normal.',
     'Stable nontoxic thyroid nodule. Mildly suppressed TSH, likely subclinical.',
     'Repeat thyroid ultrasound in 12 months. Recheck TSH and free T4 in 3 months.',
     '2026-07-09 10:38-04', '2026-07-09 10:38-04')
on conflict do nothing;
