package com.teklumezgebo.ehr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Verifies GET /api/patients/{id} returns the opened chart, enforces role plus care team, and
 * audits every attempt as a single row.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PatientAccessTests {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	UUID patientId = UUID.randomUUID();

	UUID careTeamPhysician = UUID.randomUUID();

	UUID otherPhysician = UUID.randomUUID();

	UUID careTeamNurse = UUID.randomUUID();

	@BeforeEach
	void createPatientWithCareTeam() {
		for (UUID user : new UUID[] { careTeamPhysician, otherPhysician, careTeamNurse }) {
			jdbc.sql("insert into app_user (id, display_name) values (?, 'Test User')").param(user).update();
		}
		jdbc.sql("""
				insert into patient (id, mrn, given_name, family_name, birth_date, sex, pcp_id)
				values (?, ?, 'Test', 'Patient', '1980-01-01', 'female', ?)
				""").params(patientId, "T" + patientId, careTeamPhysician).update();
		for (UUID member : new UUID[] { careTeamPhysician, careTeamNurse }) {
			jdbc.sql("insert into care_team_member (patient_id, user_id) values (?, ?)")
				.params(patientId, member)
				.update();
		}
		jdbc.sql("insert into problem (patient_id, icd10, description) values (?, 'I10', 'Essential hypertension')")
			.param(patientId)
			.update();
		jdbc.sql("insert into allergy (patient_id, substance, severity) values (?, 'Penicillin', 'severe')")
			.param(patientId)
			.update();
		for (String[] result : new String[][] { { "8.1", "2026-05-01" }, { "7.4", "2026-08-01" } }) {
			jdbc.sql("""
					insert into observation (patient_id, category, loinc, display, value, unit, ref_high, taken_at)
					values (?, 'laboratory', '4548-4', 'Hemoglobin A1c', ?::numeric, '%', 5.6, ?::timestamptz)
					""").params(patientId, result[0], result[1]).update();
		}
	}

	@Test
	void careTeamPhysicianIsAllowedAndAudited() {
		assertThat(mvc.get().uri("/api/patients/{id}", patientId).with(user(careTeamPhysician, "PHYSICIAN")))
			.hasStatusOk()
			.bodyJson()
			.satisfies(chart -> {
				assertThat(chart).extractingPath("$.patient.pcpName").isEqualTo("Test User");
				// Active members only; otherPhysician is not on the team.
				assertThat(chart).extractingPath("$.careTeam.length()").isEqualTo(2);
				assertThat(chart).extractingPath("$.problems[0].icd10").isEqualTo("I10");
				assertThat(chart).extractingPath("$.allergies[0].substance").isEqualTo("Penicillin");
				// Only the latest result per LOINC code is returned.
				assertThat(chart).extractingPath("$.labs.length()").isEqualTo(1);
				assertThat(chart).extractingPath("$.labs[0].value").isEqualTo(7.4);
			});
		assertThat(auditOutcomes(careTeamPhysician)).containsExactly("SUCCESS");
		assertThat(jdbc.sql("select count(*) from audit_event where user_id = ?")
			.param(careTeamPhysician)
			.query(Long.class)
			.single()).isEqualTo(1);
	}

	@Test
	void physicianOutsideCareTeamIsDeniedAndAudited() {
		assertThat(mvc.get().uri("/api/patients/{id}", patientId).with(user(otherPhysician, "PHYSICIAN")))
			.hasStatus(403);
		assertThat(auditOutcomes(otherPhysician)).containsExactly("DENIED");
	}

	@Test
	void careTeamNurseIsAllowedAndAudited() {
		assertThat(mvc.get().uri("/api/patients/{id}", patientId).with(user(careTeamNurse, "MA_NURSE")))
			.hasStatusOk();
		assertThat(auditOutcomes(careTeamNurse)).containsExactly("SUCCESS");
	}

	@Test
	void careTeamMemberWithoutClinicalRoleIsDeniedAndAudited() {
		assertThat(mvc.get()
			.uri("/api/patients/{id}", patientId)
			.with(jwt().jwt(token -> token.subject(careTeamNurse.toString()))))
			.hasStatus(403);
		assertThat(auditOutcomes(careTeamNurse)).containsExactly("DENIED");
	}

	@Test
	void unauthenticatedRequestIsRejected() {
		assertThat(mvc.get().uri("/api/patients/{id}", patientId)).hasStatus(401);
	}

	private static RequestPostProcessor user(UUID subject, String role) {
		return jwt().jwt(token -> token.subject(subject.toString()))
			.authorities(new SimpleGrantedAuthority("ROLE_" + role));
	}

	private List<String> auditOutcomes(UUID user) {
		return jdbc.sql("""
				select outcome from audit_event
				where user_id = ? and patient_id = ? and resource_type = 'Patient' and action = 'READ'
				""").params(user, patientId).query(String.class).list();
	}

}
