package com.teklumezgebo.ehr.audit;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only view of the audit log for the AUDITOR role. Every attempt is itself audited, but
 * successful log reads are omitted from the view; they remain in the table.
 */
@RestController
@RequestMapping("/api/audit-events")
class AuditEventController {

	static final int PAGE_SIZE = 100;

	/** One audit row with the user's display name and the patient's MRN, when known. */
	record AuditEventView(long id, OffsetDateTime at, UUID userId, String userName, String action,
			String outcome, String resourceType, String resourceId, UUID patientId, String patientMrn,
			String ip) {
	}

	private final JdbcClient jdbc;

	private final AuditLog auditLog;

	AuditEventController(JdbcClient jdbc, AuditLog auditLog) {
		this.jdbc = jdbc;
		this.auditLog = auditLog;
	}

	/** Returns the newest events first; pass the last id seen as {@code before} for the next page. */
	@GetMapping
	List<AuditEventView> list(Authentication caller, @RequestParam(required = false) Long before) {
		// Checked here rather than with @PreAuthorize so denied attempts are recorded too.
		boolean isAuditor = caller.getAuthorities()
			.stream()
			.map(GrantedAuthority::getAuthority)
			.anyMatch("ROLE_AUDITOR"::equals);
		if (!isAuditor) {
			auditLog.recordLogRead(caller, AuditLog.DENIED);
			throw new AccessDeniedException("Audit log requires the AUDITOR role");
		}

		List<AuditEventView> events = jdbc.sql("""
				select e.id, e.at, e.user_id, u.display_name as user_name, e.action, e.outcome,
				       e.resource_type, e.resource_id, e.patient_id, p.mrn as patient_mrn, host(e.ip) as ip
				from audit_event e
				left join app_user u on u.id = e.user_id
				left join patient p on p.id = e.patient_id
				where e.id < ?
				  and not (e.resource_type = ? and e.outcome = ?)
				order by e.id desc
				limit ?
				""")
			.params(before == null ? Long.MAX_VALUE : before, AuditLog.LOG_RESOURCE, AuditLog.SUCCESS, PAGE_SIZE)
			.query(AuditEventView.class)
			.list();
		auditLog.recordLogRead(caller, AuditLog.SUCCESS);
		return events;
	}

}
