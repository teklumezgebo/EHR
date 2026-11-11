package com.teklumezgebo.ehr.audit;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Writes audit_event rows. The insert trigger assigns id, timestamp, and hash chain.
 *
 * <p>Each write runs in its own transaction so the row survives a rollback of the caller's
 * transaction (a denied access throws), and so the chain's advisory lock is held only for this
 * insert.
 */
@Component
class AuditLog {

	static final String SUCCESS = "SUCCESS";

	static final String DENIED = "DENIED";

	/** An authorized access that threw before completing. */
	static final String ERROR = "ERROR";

	/** resource_type of reads of the audit log itself. */
	static final String LOG_RESOURCE = "AuditEvent";

	private final JdbcClient jdbc;

	AuditLog(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Records an access to one patient's PHI. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	void record(Authentication caller, PhiAccess access, UUID patientId, String resourceId, String outcome) {
		insert(caller, access.action(), access.resourceType(), resourceId, patientId, outcome);
	}

	/** Records a read of the audit log itself, which spans all patients. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	void recordLogRead(Authentication caller, String outcome) {
		insert(caller, "READ", LOG_RESOURCE, null, null, outcome);
	}

	private void insert(Authentication caller, String action, String resourceType, String resourceId,
			UUID patientId, String outcome) {
		jdbc.sql("""
				insert into audit_event (user_id, action, outcome, resource_type, resource_id, patient_id, ip)
				values (?, ?, ?, ?, ?, ?, cast(? as inet))
				""")
			.params(UUID.fromString(caller.getName()), action, outcome, resourceType, resourceId, patientId,
					clientIp())
			.update();
	}

	private static String clientIp() {
		return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes request
				? request.getRequest().getRemoteAddr() : null;
	}

}
