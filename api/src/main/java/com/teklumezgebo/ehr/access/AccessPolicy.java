package com.teklumezgebo.ehr.access;

import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Decides chart access from role plus relationship: the caller must hold a clinical role and be an
 * active member of the patient's care team. Both clinical roles may read the chart, room a visit,
 * write their own note, and sign; per-action rules belong here once a role-limited action exists.
 */
@Component
public class AccessPolicy {

	private static final Set<String> CHART_ROLES = Set.of("ROLE_PHYSICIAN", "ROLE_MA_NURSE");

	private final JdbcClient jdbc;

	AccessPolicy(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Returns true if the caller may read or document in the chart of the given patient. */
	public boolean canAccessChart(Authentication caller, UUID patientId) {
		boolean roleAllows = caller.getAuthorities()
			.stream()
			.map(GrantedAuthority::getAuthority)
			.anyMatch(CHART_ROLES::contains);
		return roleAllows && onCareTeam(UUID.fromString(caller.getName()), patientId);
	}

	private boolean onCareTeam(UUID userId, UUID patientId) {
		return jdbc.sql("""
				select exists (
				    select 1 from care_team_member
				    where patient_id = ? and user_id = ? and ended_at is null
				)
				""").params(patientId, userId).query(Boolean.class).single();
	}

}
