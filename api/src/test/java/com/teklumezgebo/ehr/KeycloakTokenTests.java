package com.teklumezgebo.ehr;

import static org.assertj.core.api.Assertions.assertThat;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.testcontainers.utility.MountableFile;

/**
 * Verifies the real token path: a password-grant token from Keycloak, imported with the same realm
 * as compose.yaml, passes signature, issuer, and audience checks and maps realm roles.
 */
@Import({ TestcontainersConfiguration.class, KeycloakTokenTests.KeycloakConfiguration.class })
@SpringBootTest
@AutoConfigureMockMvc
class KeycloakTokenTests {

	// Matches the physician user in infra/keycloak/ehr-realm.json.
	static final UUID PHYSICIAN = UUID.fromString("6f1c2a10-0001-4c8e-9a51-000000000001");

	@TestConfiguration(proxyBeanMethods = false)
	static class KeycloakConfiguration {

		@Bean
		KeycloakContainer keycloak() {
			return new KeycloakContainer("quay.io/keycloak/keycloak:26.8.0").withCopyFileToContainer(
					MountableFile.forHostPath("../infra/keycloak/ehr-realm.json"),
					"/opt/keycloak/data/import/ehr-realm.json");
		}

		@Bean
		DynamicPropertyRegistrar keycloakProperties(KeycloakContainer keycloak) {
			return registry -> {
				registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> keycloak.getIssuerUrl("ehr"));
				registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> keycloak.getJwksUri("ehr"));
			};
		}

	}

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	KeycloakContainer keycloak;

	@Test
	void keycloakTokenReadsCareTeamPatient() {
		UUID patientId = UUID.randomUUID();
		jdbc.sql("insert into app_user (id, display_name) values (?, 'Maya Chen, MD') on conflict do nothing")
			.param(PHYSICIAN)
			.update();
		jdbc.sql("""
				insert into patient (id, mrn, given_name, family_name, birth_date, sex)
				values (?, ?, 'Test', 'Patient', '1980-01-01', 'female')
				""").params(patientId, "K" + patientId).update();
		jdbc.sql("insert into care_team_member (patient_id, user_id) values (?, ?)").params(patientId, PHYSICIAN).update();

		assertThat(mvc.get()
			.uri("/api/patients/{id}", patientId)
			.header("Authorization", "Bearer " + physicianToken())).hasStatusOk();
	}

	// The web client only allows the browser code flow; password grant is enabled here so the test
	// can log in without a browser.
	private String physicianToken() {
		try (var admin = keycloak.getKeycloakAdminClient()) {
			var clients = admin.realm("ehr").clients();
			var web = clients.findByClientId("ehr-web").getFirst();
			web.setDirectAccessGrantsEnabled(true);
			clients.get(web.getId()).update(web);
		}
		return keycloak.getAccessToken("ehr", "ehr-web", "physician", "demo");
	}

}
