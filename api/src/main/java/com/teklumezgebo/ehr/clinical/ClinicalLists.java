package com.teklumezgebo.ehr.clinical;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Problem list, allergies, and recent labs for one patient. Not exposed over HTTP; callers must enforce access
 * and auditing themselves.
 */
@Component
public class ClinicalLists {

	public record Problem(UUID id, String icd10, String description, LocalDate onset, String status) {
	}

	public record Allergy(UUID id, String substance, String reaction, String severity) {
	}

	public record LabResult(UUID id, String loinc, String display, String panel, BigDecimal value, String unit,
			BigDecimal refLow, BigDecimal refHigh, OffsetDateTime takenAt) {
	}

	private final JdbcClient jdbc;

	ClinicalLists(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Returns all problems, active first, then by onset. */
	public List<Problem> problems(UUID patientId) {
		return jdbc.sql("""
				select id, icd10, description, onset, status
				from problem
				where patient_id = ?
				order by status <> 'active', onset nulls last, description
				""").param(patientId).query(Problem.class).list();
	}

	/** Returns all recorded allergies; an empty list means none recorded, not "no known allergies". */
	public List<Allergy> allergies(UUID patientId) {
		return jdbc.sql("""
				select id, substance, reaction, severity
				from allergy
				where patient_id = ?
				order by substance
				""").param(patientId).query(Allergy.class).list();
	}

	/**
	 * Returns the most recent lab result for each LOINC code. Standalone tests come first, then
	 * panels by name.
	 */
	public List<LabResult> recentLabs(UUID patientId) {
		return jdbc.sql("""
				select * from (
				    select distinct on (loinc)
				           id, loinc, display, panel, value, unit, ref_low, ref_high, taken_at
				    from observation
				    where patient_id = ? and category = 'laboratory'
				    order by loinc, taken_at desc
				) latest
				order by panel nulls first, display
				""").param(patientId).query(LabResult.class).list();
	}

}
