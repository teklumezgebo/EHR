package com.teklumezgebo.ehr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Verifies the encounter routes: care-team and role checks, validation, per-author notes with
 * optimistic locking, Sign Visit locking, the snapshot, and an audit row for every attempt.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class EncounterTests {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	UUID patientId = UUID.randomUUID();

	UUID encounterId = UUID.randomUUID();

	UUID physician = UUID.randomUUID();

	UUID nurse = UUID.randomUUID();

	UUID outsider = UUID.randomUUID();

	@BeforeEach
	void createPatientWithOpenEncounter() {
		insertUser(physician, "Test Physician, MD");
		insertUser(nurse, "Test Nurse, MA");
		insertUser(outsider, "Outside Physician, MD");
		insertPatient(patientId);
		for (UUID member : new UUID[] { physician, nurse }) {
			jdbc.sql("insert into care_team_member (patient_id, user_id) values (?, ?)").params(patientId, member).update();
		}
		insertEncounter(encounterId, patientId, "2026-09-02 09:00-04", "Diabetes Follow Up");
	}

	// Create

	@Test
	void careTeamNurseCreatesEncounter() {
		assertThat(post("/api/patients/{p}/encounters", nurse, "MA_NURSE", """
				{"providerId": "%s", "type": "Office Visit", "reason": "Cough"}
				""".formatted(physician), patientId))
			.hasStatus(201)
			.bodyJson()
			.satisfies(header -> {
				assertThat(header).extractingPath("$.providerName").isEqualTo("Test Physician, MD");
				assertThat(header).extractingPath("$.department").isEqualTo("Family Medicine");
				assertThat(header).extractingPath("$.status").isEqualTo("OPEN");
			});
		assertThat(encounterCount()).isEqualTo(2);
		assertThat(audits(nurse)).containsExactly("CREATE Encounter SUCCESS");
	}

	@Test
	void physicianOutsideCareTeamCannotCreateEncounter() {
		assertThat(post("/api/patients/{p}/encounters", outsider, "PHYSICIAN", """
				{"providerId": "%s", "type": "Office Visit"}
				""".formatted(outsider), patientId)).hasStatus(403);
		assertThat(encounterCount()).isEqualTo(1);
		assertThat(audits(outsider)).containsExactly("CREATE Encounter DENIED");
	}

	@Test
	void providerMustBeOnCareTeam() {
		assertThat(post("/api/patients/{p}/encounters", nurse, "MA_NURSE", """
				{"providerId": "%s", "type": "Office Visit"}
				""".formatted(outsider), patientId)).hasStatus(400);
		assertThat(encounterCount()).isEqualTo(1);
		assertThat(audits(nurse)).containsExactly("CREATE Encounter ERROR");
	}

	// Vitals

	@Test
	void nurseRecordsVitalsWithComputedBmi() {
		assertThat(post(vitalsUri(), nurse, "MA_NURSE", """
				{"systolic": 128, "diastolic": 82, "heartRate": 72, "tempF": 98.6, "weightLb": 160, "heightIn": 68}
				""", patientId, encounterId))
			.hasStatus(201)
			.bodyJson()
			.extractingPath("$.length()")
			.isEqualTo(7);
		assertThat(vitalValue("39156-5")).isEqualByComparingTo("24.3");
		assertThat(vitalValue("8480-6")).isEqualByComparingTo("128");
		assertThat(audits(nurse)).containsExactly("CREATE Observation SUCCESS");
		assertThat(lastAuditResourceId(nurse)).isEqualTo(encounterId.toString());
	}

	@Test
	void invalidVitalsAreRejectedAndAudited() {
		for (String body : new String[] { """
				{"systolic": 400, "diastolic": 80}
				""", "{}", """
				{"systolic": 120}
				""" }) {
			assertThat(post(vitalsUri(), nurse, "MA_NURSE", body, patientId, encounterId)).hasStatus(400);
		}
		assertThat(vitalCount()).isZero();
		assertThat(audits(nurse)).containsExactly("CREATE Observation ERROR", "CREATE Observation ERROR",
				"CREATE Observation ERROR");
	}

	@Test
	void accessIsCheckedBeforeValidation() {
		assertThat(post(vitalsUri(), outsider, "PHYSICIAN", "{}", patientId, encounterId)).hasStatus(403);
		assertThat(audits(outsider)).containsExactly("CREATE Observation DENIED");
	}

	// Notes

	@Test
	void physicianCreatesThenUpdatesProgressNote() {
		assertThat(putNote(physician, "PHYSICIAN", """
				{"subjective": "first draft"}
				""")).hasStatusOk().bodyJson().satisfies(note -> {
			assertThat(note).extractingPath("$.version").isEqualTo(1);
			assertThat(note).extractingPath("$.type").isEqualTo("PROGRESS");
		});
		assertThat(putNote(physician, "PHYSICIAN", """
				{"subjective": "second draft", "version": 1}
				""")).hasStatusOk().bodyJson().extractingPath("$.version").isEqualTo(2);
		assertThat(noteSubjectives()).containsExactly("second draft");
		assertThat(audits(physician)).containsExactly("UPDATE DocumentReference SUCCESS",
				"UPDATE DocumentReference SUCCESS");
	}

	@Test
	void staleVersionIsRejected() {
		putNote(physician, "PHYSICIAN", """
				{"subjective": "v1"}
				""").assertThat().hasStatusOk();
		putNote(physician, "PHYSICIAN", """
				{"subjective": "v2", "version": 1}
				""").assertThat().hasStatusOk();
		assertThat(putNote(physician, "PHYSICIAN", """
				{"subjective": "edited from a stale copy", "version": 1}
				""")).hasStatus(409);
		assertThat(noteSubjectives()).containsExactly("v2");
		assertThat(audits(physician)).last().isEqualTo("UPDATE DocumentReference ERROR");
	}

	@Test
	void secondCreateIsRejected() {
		putNote(physician, "PHYSICIAN", "{}").assertThat().hasStatusOk();
		assertThat(putNote(physician, "PHYSICIAN", """
				{"subjective": "overwrite"}
				""")).hasStatus(409);
		assertThat(noteSubjectives()).containsExactly("");
	}

	@Test
	void eachAuthorKeepsTheirOwnNote() {
		assertThat(putNote(nurse, "MA_NURSE", """
				{"subjective": "rooming"}
				""")).hasStatusOk().bodyJson().extractingPath("$.type").isEqualTo("ROOMING");
		putNote(physician, "PHYSICIAN", """
				{"subjective": "progress"}
				""").assertThat().hasStatusOk();
		assertThat(noteSubjectives()).containsExactlyInAnyOrder("rooming", "progress");
	}

	@Test
	void signedNoteCannotBeEdited() {
		putNote(physician, "PHYSICIAN", "{}").assertThat().hasStatusOk();
		assertThat(post(encounterUri() + "/note/sign", physician, "PHYSICIAN", null, patientId, encounterId))
			.hasStatusOk();
		assertThat(putNote(physician, "PHYSICIAN", """
				{"subjective": "late edit", "version": 1}
				""")).hasStatus(409);
		assertThat(post(encounterUri() + "/note/sign", physician, "PHYSICIAN", null, patientId, encounterId))
			.hasStatus(409);
	}

	// Sign Visit

	@Test
	void nurseSignsVisitOnceEveryNoteIsSigned() {
		putNote(physician, "PHYSICIAN", "{}").assertThat().hasStatusOk();
		assertThat(signVisit(nurse, "MA_NURSE")).hasStatus(409);

		post(encounterUri() + "/note/sign", physician, "PHYSICIAN", null, patientId, encounterId).assertThat()
			.hasStatusOk();
		assertThat(signVisit(nurse, "MA_NURSE")).hasStatusOk().bodyJson().satisfies(header -> {
			assertThat(header).extractingPath("$.status").isEqualTo("SIGNED");
			assertThat(header).extractingPath("$.signedByName").isEqualTo("Test Nurse, MA");
		});
		assertThat(audits(nurse)).containsExactly("SIGN Encounter ERROR", "SIGN Encounter SUCCESS");
	}

	@Test
	void signedEncounterRejectsEveryWrite() {
		signVisit(nurse, "MA_NURSE").assertThat().hasStatusOk();

		assertThat(post(vitalsUri(), nurse, "MA_NURSE", """
				{"heartRate": 70}
				""", patientId, encounterId)).hasStatus(409);
		assertThat(putNote(physician, "PHYSICIAN", "{}")).hasStatus(409);
		assertThat(signVisit(physician, "PHYSICIAN")).hasStatus(409);
		assertThat(vitalCount()).isZero();
		assertThat(noteSubjectives()).isEmpty();
		assertThat(audits(physician)).containsExactly("UPDATE DocumentReference ERROR", "SIGN Encounter ERROR");
	}

	// Snapshot and list

	@Test
	void snapshotReturnsHeaderVitalsAndNotesAsOneAudit() {
		post(vitalsUri(), nurse, "MA_NURSE", """
				{"systolic": 130, "diastolic": 80}
				""", patientId, encounterId).assertThat().hasStatus(201);
		putNote(physician, "PHYSICIAN", """
				{"assessment": "Stable"}
				""").assertThat().hasStatusOk();

		assertThat(mvc.get().uri(encounterUri(), patientId, encounterId).with(user(nurse, "MA_NURSE")))
			.hasStatusOk()
			.bodyJson()
			.satisfies(snapshot -> {
				assertThat(snapshot).extractingPath("$.encounter.providerName").isEqualTo("Test Physician, MD");
				assertThat(snapshot).extractingPath("$.encounter.reason").isEqualTo("Diabetes Follow Up");
				assertThat(snapshot).extractingPath("$.vitals.length()").isEqualTo(2);
				assertThat(snapshot).extractingPath("$.notes[0].assessment").isEqualTo("Stable");
				assertThat(snapshot).extractingPath("$.notes[0].version").isEqualTo(1);
			});
		assertThat(audits(nurse)).containsExactly("CREATE Observation SUCCESS", "READ Encounter SUCCESS");
		assertThat(lastAuditResourceId(nurse)).isEqualTo(encounterId.toString());
	}

	@Test
	void encounterOfAnotherPatientIsNotReachable() {
		UUID otherPatient = UUID.randomUUID();
		UUID otherEncounter = UUID.randomUUID();
		insertPatient(otherPatient);
		insertEncounter(otherEncounter, otherPatient, "2026-09-03 10:00-04", "Confidential reason");

		assertThat(mvc.get().uri(encounterUri(), patientId, otherEncounter).with(user(physician, "PHYSICIAN")))
			.hasStatus(404)
			.bodyText()
			.doesNotContain("Confidential reason");
		assertThat(post(vitalsUri(), physician, "PHYSICIAN", """
				{"heartRate": 70}
				""", patientId, otherEncounter)).hasStatus(404);
		assertThat(jdbc.sql("select count(*) from observation where encounter_id = ?")
			.param(otherEncounter)
			.query(Long.class)
			.single()).isZero();
	}

	@Test
	void listReturnsNewestFirst() {
		UUID older = UUID.randomUUID();
		insertEncounter(older, patientId, "2026-03-01 09:00-05", "Annual Physical");

		assertThat(mvc.get().uri("/api/patients/{p}/encounters", patientId).with(user(nurse, "MA_NURSE")))
			.hasStatusOk()
			.bodyJson()
			.extractingPath("$[*].id")
			.asArray()
			.containsExactly(encounterId.toString(), older.toString());
	}

	@Test
	void unauthenticatedRequestIsRejected() {
		assertThat(mvc.get().uri(encounterUri(), patientId, encounterId)).hasStatus(401);
	}

	private MvcTestResult post(String uri, UUID subject, String role, String json, Object... uriVars) {
		var request = mvc.post().uri(uri, uriVars).with(user(subject, role));
		return json == null ? request.exchange()
				: request.contentType(MediaType.APPLICATION_JSON).content(json).exchange();
	}

	private MvcTestResult putNote(UUID subject, String role, String json) {
		return mvc.put()
			.uri(encounterUri() + "/note", patientId, encounterId)
			.with(user(subject, role))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json)
			.exchange();
	}

	private MvcTestResult signVisit(UUID subject, String role) {
		return post(encounterUri() + "/sign", subject, role, null, patientId, encounterId);
	}

	private static String encounterUri() {
		return "/api/patients/{p}/encounters/{e}";
	}

	private static String vitalsUri() {
		return encounterUri() + "/vitals";
	}

	private static RequestPostProcessor user(UUID subject, String role) {
		return jwt().jwt(token -> token.subject(subject.toString()))
			.authorities(new SimpleGrantedAuthority("ROLE_" + role));
	}

	private void insertUser(UUID id, String name) {
		jdbc.sql("insert into app_user (id, display_name, department) values (?, ?, 'Family Medicine')")
			.params(id, name)
			.update();
	}

	private void insertPatient(UUID id) {
		jdbc.sql("""
				insert into patient (id, mrn, given_name, family_name, birth_date, sex)
				values (?, ?, 'Test', 'Patient', '1970-01-01', 'male')
				""").params(id, "T" + id).update();
	}

	private void insertEncounter(UUID id, UUID patient, String startAt, String reason) {
		jdbc.sql("""
				insert into encounter (id, patient_id, provider_id, department, type, reason, start_at)
				values (?, ?, ?, 'Family Medicine', 'Office Visit', ?, ?::timestamptz)
				""").params(id, patient, physician, reason, startAt).update();
	}

	private long encounterCount() {
		return jdbc.sql("select count(*) from encounter where patient_id = ?").param(patientId).query(Long.class).single();
	}

	private long vitalCount() {
		return jdbc.sql("select count(*) from observation where encounter_id = ?")
			.param(encounterId)
			.query(Long.class)
			.single();
	}

	private BigDecimal vitalValue(String loinc) {
		return jdbc.sql("""
				select value from observation
				where encounter_id = ? and category = 'vital-signs' and loinc = ?
				""").params(encounterId, loinc).query(BigDecimal.class).single();
	}

	private List<String> noteSubjectives() {
		return jdbc.sql("select subjective from note where encounter_id = ?")
			.param(encounterId)
			.query(String.class)
			.list();
	}

	/** Each audit row for this patient as "ACTION ResourceType OUTCOME", oldest first. */
	private List<String> audits(UUID user) {
		return jdbc.sql("""
				select action || ' ' || resource_type || ' ' || outcome from audit_event
				where user_id = ? and patient_id = ?
				order by id
				""").params(user, patientId).query(String.class).list();
	}

	private String lastAuditResourceId(UUID user) {
		return jdbc.sql("select resource_id from audit_event where user_id = ? order by id desc limit 1")
			.param(user)
			.query(String.class)
			.single();
	}

}
