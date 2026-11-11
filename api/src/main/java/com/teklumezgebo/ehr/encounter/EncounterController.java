package com.teklumezgebo.ehr.encounter;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.teklumezgebo.ehr.audit.PhiAccess;
import jakarta.validation.Validator;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Encounters for one patient: creation, rooming vitals, per-author SOAP notes, Sign Visit, and the
 * encounter snapshot.
 *
 * <p>Request bodies are validated inside each method rather than with {@code @Valid}, so a rejected
 * payload runs after the access check and is audited as ERROR. Every query matches both the patient
 * and the encounter id, so an encounter is never reachable through another patient's chart.
 */
@RestController
@RequestMapping("/api/patients/{patientId}/encounters")
class EncounterController {

	/** The encounter header: "9/2/2026 visit with Dr. X for Office Visit - reason". */
	record EncounterHeader(UUID id, OffsetDateTime startAt, String providerName, String department, String type,
			String reason, String status, OffsetDateTime signedAt, String signedByName) {
	}

	/** One vital-sign reading, stored as an Observation. */
	record Vital(UUID id, String loinc, String display, String panel, BigDecimal value, String unit,
			OffsetDateTime takenAt) {
	}

	record Note(UUID id, UUID authorId, String authorName, String type, String subjective, String objective,
			String assessment, String plan, int version, OffsetDateTime updatedAt, OffsetDateTime signedAt) {
	}

	/** Everything shown when an encounter is opened, returned together as one audited access. */
	record Snapshot(EncounterHeader encounter, List<Vital> vitals, List<Note> notes) {
	}

	record CreateRequest(@NotNull UUID providerId, @NotBlank @Size(max = 100) String type,
			@Size(max = 200) String reason, @PastOrPresent OffsetDateTime startAt) {
	}

	/** One set of vitals in US units. BMI is computed when weight and height are both present. */
	record VitalsRequest(@Min(50) @Max(300) Integer systolic, @Min(20) @Max(200) Integer diastolic,
			@Min(20) @Max(300) Integer heartRate, @Min(4) @Max(80) Integer respRate,
			@DecimalMin("90") @DecimalMax("110") BigDecimal tempF, @Min(50) @Max(100) Integer spo2,
			@DecimalMin("1") @DecimalMax("1000") BigDecimal weightLb,
			@DecimalMin("10") @DecimalMax("108") BigDecimal heightIn, @PastOrPresent OffsetDateTime takenAt) {

		@AssertTrue(message = "must include at least one measurement")
		boolean isAnyMeasured() {
			return Stream.of(systolic, diastolic, heartRate, respRate, tempF, spo2, weightLb, heightIn)
				.anyMatch(Objects::nonNull);
		}

		@AssertTrue(message = "systolic and diastolic must be recorded together, with systolic higher")
		boolean isBloodPressureValid() {
			return systolic == null && diastolic == null
					|| systolic != null && diastolic != null && systolic > diastolic;
		}

	}

	/** A note save. Omit {@code version} to create the note; send the version being edited to update it. */
	record NoteRequest(@Size(max = 20_000) String subjective, @Size(max = 20_000) String objective,
			@Size(max = 20_000) String assessment, @Size(max = 20_000) String plan, @Positive Integer version) {
	}

	private record Reading(String loinc, String display, String panel, BigDecimal value, String unit) {
	}

	private static final String HEADER_SELECT = """
			select e.id, e.start_at, pr.display_name as provider_name, e.department, e.type, e.reason,
			       e.status, e.signed_at, s.display_name as signed_by_name
			from encounter e
			join app_user pr on pr.id = e.provider_id
			left join app_user s on s.id = e.signed_by
			""";

	private static final String NOTE_SELECT = """
			select n.id, n.author_id, a.display_name as author_name, n.type, n.subjective, n.objective,
			       n.assessment, n.plan, n.version, n.updated_at, n.signed_at
			from note n
			join app_user a on a.id = n.author_id
			""";

	private static final String BLOOD_PRESSURE = "Blood Pressure";

	private final JdbcClient jdbc;

	private final Validator validator;

	EncounterController(JdbcClient jdbc, Validator validator) {
		this.jdbc = jdbc;
		this.validator = validator;
	}

	/** Lists the patient's encounters, newest first. */
	@GetMapping
	@PhiAccess(resourceType = "Encounter")
	List<EncounterHeader> list(@PathVariable UUID patientId) {
		return jdbc.sql(HEADER_SELECT + "where e.patient_id = ? order by e.start_at desc")
			.param(patientId)
			.query(EncounterHeader.class)
			.list();
	}

	/**
	 * Opens an encounter with a provider on the patient's care team, in that provider's department.
	 * The audit row's resource_id is the patient, since the encounter id does not exist yet.
	 */
	@PostMapping
	@ResponseStatus(CREATED)
	@Transactional
	@PhiAccess(resourceType = "Encounter", action = "CREATE")
	EncounterHeader create(@PathVariable UUID patientId, @RequestBody CreateRequest body) {
		validate(body);
		String department = jdbc.sql("""
				select u.department from app_user u
				join care_team_member c on c.user_id = u.id
				where c.patient_id = ? and u.id = ? and c.ended_at is null
				""")
			.params(patientId, body.providerId())
			.query(String.class)
			.optional()
			.orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Provider is not on this patient's care team"));
		if (department == null) {
			throw new ResponseStatusException(BAD_REQUEST, "Provider has no department");
		}
		UUID encounterId = jdbc.sql("""
				insert into encounter (patient_id, provider_id, department, type, reason, start_at)
				values (?, ?, ?, ?, ?, coalesce(?, now()))
				returning id
				""")
			.params(patientId, body.providerId(), department, body.type(), body.reason(), body.startAt())
			.query(UUID.class)
			.single();
		return header(patientId, encounterId);
	}

	@GetMapping("/{encounterId}")
	@PhiAccess(resourceType = "Encounter", resourceParam = "encounterId")
	Snapshot get(@PathVariable UUID patientId, @PathVariable UUID encounterId) {
		EncounterHeader header = header(patientId, encounterId);
		List<Vital> vitals = jdbc.sql("""
				select id, loinc, display, panel, value, unit, taken_at
				from observation
				where encounter_id = ? and category = 'vital-signs'
				order by taken_at desc, display
				""").param(encounterId).query(Vital.class).list();
		List<Note> notes = jdbc.sql(NOTE_SELECT + "where n.encounter_id = ? order by n.type, n.updated_at")
			.param(encounterId)
			.query(Note.class)
			.list();
		return new Snapshot(header, vitals, notes);
	}

	/** Records one set of vitals. Readings are never edited; a correction is a new set. */
	@PostMapping("/{encounterId}/vitals")
	@ResponseStatus(CREATED)
	@Transactional
	@PhiAccess(resourceType = "Observation", action = "CREATE", resourceParam = "encounterId")
	List<Vital> recordVitals(@PathVariable UUID patientId, @PathVariable UUID encounterId,
			@RequestBody VitalsRequest body) {
		validate(body);
		lockOpen(patientId, encounterId);
		OffsetDateTime takenAt = body.takenAt() != null ? body.takenAt() : OffsetDateTime.now();
		return readings(body).stream()
			.map(r -> jdbc.sql("""
					insert into observation (patient_id, encounter_id, category, loinc, display, panel, value, unit, taken_at)
					values (?, ?, 'vital-signs', ?, ?, ?, ?, ?, ?)
					returning id, loinc, display, panel, value, unit, taken_at
					""")
				.params(patientId, encounterId, r.loinc(), r.display(), r.panel(), r.value(), r.unit(), takenAt)
				.query(Vital.class)
				.single())
			.toList();
	}

	/**
	 * Creates or updates the caller's own note on this encounter. The note type follows the caller's
	 * role. Updates must name the version they were edited from, so a save made from a stale copy is
	 * rejected instead of overwriting newer text.
	 */
	@PutMapping("/{encounterId}/note")
	@Transactional
	@PhiAccess(resourceType = "DocumentReference", action = "UPDATE", resourceParam = "encounterId")
	Note saveNote(@PathVariable UUID patientId, @PathVariable UUID encounterId, @RequestBody NoteRequest body,
			Authentication caller) {
		validate(body);
		lockOpen(patientId, encounterId);
		UUID author = UUID.fromString(caller.getName());
		List<Object> text = Stream.of(body.subjective(), body.objective(), body.assessment(), body.plan())
			.map(field -> (Object) Objects.requireNonNullElse(field, ""))
			.toList();

		if (body.version() == null) {
			int created = jdbc.sql("""
					insert into note (encounter_id, author_id, type, subjective, objective, assessment, plan)
					values (?, ?, ?, ?, ?, ?, ?)
					on conflict (encounter_id, author_id) do nothing
					""").params(encounterId, author, noteType(caller)).params(text).update();
			if (created == 0) {
				throw new ResponseStatusException(CONFLICT, "Note already exists; send its version to update it");
			}
			return note(encounterId, author);
		}

		Note current = note(encounterId, author);
		if (current.signedAt() != null) {
			throw new ResponseStatusException(CONFLICT, "Note is signed");
		}
		if (current.version() != body.version()) {
			throw new ResponseStatusException(CONFLICT,
					"Note was saved elsewhere; current version is " + current.version());
		}
		jdbc.sql("""
				update note
				set subjective = ?, objective = ?, assessment = ?, plan = ?, version = version + 1, updated_at = now()
				where id = ?
				""").params(text).param(current.id()).update();
		return note(encounterId, author);
	}

	/** Signs the caller's own note, which locks it. */
	@PostMapping("/{encounterId}/note/sign")
	@Transactional
	@PhiAccess(resourceType = "DocumentReference", action = "SIGN", resourceParam = "encounterId")
	Note signNote(@PathVariable UUID patientId, @PathVariable UUID encounterId, Authentication caller) {
		lockOpen(patientId, encounterId);
		UUID author = UUID.fromString(caller.getName());
		if (note(encounterId, author).signedAt() != null) {
			throw new ResponseStatusException(CONFLICT, "Note is already signed");
		}
		jdbc.sql("update note set signed_at = now() where encounter_id = ? and author_id = ?")
			.params(encounterId, author)
			.update();
		return note(encounterId, author);
	}

	/** Sign Visit: locks the encounter, its notes, and its vitals. Every note must be signed first. */
	@PostMapping("/{encounterId}/sign")
	@Transactional
	@PhiAccess(resourceType = "Encounter", action = "SIGN", resourceParam = "encounterId")
	EncounterHeader sign(@PathVariable UUID patientId, @PathVariable UUID encounterId, Authentication caller) {
		lockOpen(patientId, encounterId);
		boolean unsignedNotes = jdbc.sql("select exists (select 1 from note where encounter_id = ? and signed_at is null)")
			.param(encounterId)
			.query(Boolean.class)
			.single();
		if (unsignedNotes) {
			throw new ResponseStatusException(CONFLICT, "Every note must be signed before the visit is signed");
		}
		jdbc.sql("update encounter set status = 'SIGNED', signed_at = now(), signed_by = ? where id = ?")
			.params(UUID.fromString(caller.getName()), encounterId)
			.update();
		return header(patientId, encounterId);
	}

	private EncounterHeader header(UUID patientId, UUID encounterId) {
		return jdbc.sql(HEADER_SELECT + "where e.id = ? and e.patient_id = ?")
			.params(encounterId, patientId)
			.query(EncounterHeader.class)
			.optional()
			.orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Encounter not found"));
	}

	private Note note(UUID encounterId, UUID author) {
		return jdbc.sql(NOTE_SELECT + "where n.encounter_id = ? and n.author_id = ?")
			.params(encounterId, author)
			.query(Note.class)
			.optional()
			.orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "No note by this user on this encounter"));
	}

	/**
	 * Locks the encounter row for the rest of the transaction and rejects writes to a signed
	 * encounter. Holding the lock keeps Sign Visit from landing between this check and the write.
	 */
	private void lockOpen(UUID patientId, UUID encounterId) {
		String status = jdbc.sql("select status from encounter where id = ? and patient_id = ? for update")
			.params(encounterId, patientId)
			.query(String.class)
			.optional()
			.orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Encounter not found"));
		if (status.equals("SIGNED")) {
			throw new ResponseStatusException(CONFLICT, "Encounter is signed");
		}
	}

	private <T> void validate(T body) {
		var violations = validator.validate(body);
		if (!violations.isEmpty()) {
			throw new ResponseStatusException(BAD_REQUEST, violations.stream()
				.map(v -> v.getPropertyPath() + " " + v.getMessage())
				.sorted()
				.collect(Collectors.joining("; ")));
		}
	}

	private static String noteType(Authentication caller) {
		boolean physician = caller.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_PHYSICIAN"));
		return physician ? "PROGRESS" : "ROOMING";
	}

	/** Maps a vitals set to Observation rows, using LOINC codes and UCUM units from the FHIR vital-signs profile. */
	private static List<Reading> readings(VitalsRequest v) {
		List<Reading> rows = new ArrayList<>();
		add(rows, "8480-6", "Systolic blood pressure", BLOOD_PRESSURE, v.systolic(), "mm[Hg]");
		add(rows, "8462-4", "Diastolic blood pressure", BLOOD_PRESSURE, v.diastolic(), "mm[Hg]");
		add(rows, "8867-4", "Heart rate", null, v.heartRate(), "/min");
		add(rows, "9279-1", "Respiratory rate", null, v.respRate(), "/min");
		add(rows, "8310-5", "Body temperature", null, v.tempF(), "[degF]");
		add(rows, "59408-5", "Oxygen saturation by pulse oximetry", null, v.spo2(), "%");
		add(rows, "29463-7", "Body weight", null, v.weightLb(), "[lb_av]");
		add(rows, "8302-2", "Body height", null, v.heightIn(), "[in_i]");
		if (v.weightLb() != null && v.heightIn() != null) {
			add(rows, "39156-5", "Body mass index", null, bmi(v.weightLb(), v.heightIn()), "kg/m2");
		}
		return rows;
	}

	/** BMI from US units: 703 × lb / in², to one decimal. */
	private static BigDecimal bmi(BigDecimal weightLb, BigDecimal heightIn) {
		return weightLb.multiply(BigDecimal.valueOf(703)).divide(heightIn.pow(2), 1, RoundingMode.HALF_UP);
	}

	private static void add(List<Reading> rows, String loinc, String display, String panel, Number value, String unit) {
		if (value != null) {
			rows.add(new Reading(loinc, display, panel, new BigDecimal(value.toString()), unit));
		}
	}

}
