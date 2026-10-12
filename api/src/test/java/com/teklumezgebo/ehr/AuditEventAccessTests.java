package com.teklumezgebo.ehr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.List;
import java.util.UUID;
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
 * Verifies GET /api/audit-events is limited to auditors, every attempt is audited, and successful
 * log reads are hidden from the view.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuditEventAccessTests {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	UUID caller = UUID.randomUUID();

	@Test
	void auditorIsAllowedAndAudited() {
		assertThat(mvc.get().uri("/api/audit-events").with(user(caller, "AUDITOR"))).hasStatusOk();
		assertThat(logReadOutcomes()).containsExactly("SUCCESS");
	}

	@Test
	void successfulLogReadsAreHiddenFromView() {
		mvc.get().uri("/api/audit-events").with(user(caller, "AUDITOR")).exchange();
		assertThat(mvc.get().uri("/api/audit-events").with(user(caller, "AUDITOR")))
			.bodyJson()
			.extractingPath("$[?(@.userId == '%s')]".formatted(caller))
			.asArray()
			.isEmpty();
		assertThat(logReadOutcomes()).containsExactly("SUCCESS", "SUCCESS");
	}

	@Test
	void deniedLogReadsStayInView() {
		mvc.get().uri("/api/audit-events").with(user(caller, "PHYSICIAN")).exchange();
		assertThat(mvc.get().uri("/api/audit-events").with(user(UUID.randomUUID(), "AUDITOR")))
			.bodyJson()
			.extractingPath("$[0].userId")
			.isEqualTo(caller.toString());
	}

	@Test
	void physicianIsDeniedAndAudited() {
		assertThat(mvc.get().uri("/api/audit-events").with(user(caller, "PHYSICIAN"))).hasStatus(403);
		assertThat(logReadOutcomes()).containsExactly("DENIED");
	}

	@Test
	void unauthenticatedRequestIsRejected() {
		assertThat(mvc.get().uri("/api/audit-events")).hasStatus(401);
	}

	private static RequestPostProcessor user(UUID subject, String role) {
		return jwt().jwt(token -> token.subject(subject.toString()))
			.authorities(new SimpleGrantedAuthority("ROLE_" + role));
	}

	private List<String> logReadOutcomes() {
		return jdbc.sql("""
				select outcome from audit_event
				where user_id = ? and resource_type = 'AuditEvent' and action = 'READ'
				""").param(caller).query(String.class).list();
	}

}
