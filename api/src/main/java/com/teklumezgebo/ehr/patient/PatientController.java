package com.teklumezgebo.ehr.patient;

import com.teklumezgebo.ehr.audit.PhiAccess;
import com.teklumezgebo.ehr.clinical.ClinicalLists;
import com.teklumezgebo.ehr.clinical.ClinicalLists.Allergy;
import com.teklumezgebo.ehr.clinical.ClinicalLists.LabResult;
import com.teklumezgebo.ehr.clinical.ClinicalLists.Problem;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patients")
class PatientController {

	/** Fields shown in the Storyboard sidebar. */
	record PatientSummary(UUID id, String mrn, String givenName, String familyName, LocalDate birthDate,
			String sex, String language, String codeStatus, String photoUrl, String pcpName, String coverage) {
	}

	/** An active care-team member; the encounter form picks its provider from this list. */
	record CareTeamMember(UUID id, String name, String department) {
	}

	/** Everything shown when a chart is opened, returned together so the open is one audited access. */
	record Chart(PatientSummary patient, List<CareTeamMember> careTeam, List<Problem> problems,
			List<Allergy> allergies, List<LabResult> labs) {
	}

	private final JdbcClient jdbc;

	private final ClinicalLists clinicalLists;

	PatientController(JdbcClient jdbc, ClinicalLists clinicalLists) {
		this.jdbc = jdbc;
		this.clinicalLists = clinicalLists;
	}

	/**
	 * Opens a chart: the Storyboard and SnapShot in one response, so each open is a single audited
	 * access. {@link PhiAccess} checks role and care team before this runs and records the outcome.
	 */
	@GetMapping("/{patientId}")
	@PhiAccess(resourceType = "Patient")
	Chart get(@PathVariable UUID patientId) {
		PatientSummary patient = jdbc.sql("""
				select p.id, p.mrn, p.given_name, p.family_name, p.birth_date, p.sex, p.language,
				       p.code_status, p.photo_url, pcp.display_name as pcp_name, p.coverage
				from patient p
				left join app_user pcp on pcp.id = p.pcp_id
				where p.id = ?
				""").param(patientId).query(PatientSummary.class).single();
		List<CareTeamMember> careTeam = jdbc.sql("""
				select u.id, u.display_name as name, u.department
				from care_team_member c
				join app_user u on u.id = c.user_id
				where c.patient_id = ? and c.ended_at is null
				order by u.display_name
				""").param(patientId).query(CareTeamMember.class).list();
		return new Chart(patient, careTeam, clinicalLists.problems(patientId), clinicalLists.allergies(patientId),
				clinicalLists.recentLabs(patientId));
	}

}
