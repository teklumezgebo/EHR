package com.teklumezgebo.ehr;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** Verifies the dev profile loads the seed against the current schema. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("dev")
class DevSeedTests {

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void seedsDemoPatients() {
		assertThat(jdbc.queryForObject("select count(*) from patient", Integer.class)).isEqualTo(4);
	}

}
