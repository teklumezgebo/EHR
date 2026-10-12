package com.teklumezgebo.ehr;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Postgres provisioned with the same init script as compose.yaml, so tests connect as ehr_app and
 * migrate as ehr_owner exactly like the deployed app.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:16"))
			.withDatabaseName("ehr")
			.withEnv("EHR_OWNER_PASSWORD", "ehr_owner")
			.withEnv("EHR_APP_PASSWORD", "ehr_app")
			.withCopyFileToContainer(MountableFile.forHostPath("../infra/postgres/init/01-roles.sh", 0755),
					"/docker-entrypoint-initdb.d/01-roles.sh");
	}

	// Only the URL comes from the container; @ServiceConnection would also replace the credentials.
	@Bean
	DynamicPropertyRegistrar postgresProperties(PostgreSQLContainer postgres) {
		return registry -> registry.add("spring.datasource.url", postgres::getJdbcUrl);
	}

}
