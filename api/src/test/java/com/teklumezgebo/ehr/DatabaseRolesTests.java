package com.teklumezgebo.ehr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Verifies the runtime user cannot change the schema, delete rows, or edit migration history. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class DatabaseRolesTests {

	@Autowired
	DataSource dataSource;

	@Autowired
	PostgreSQLContainer postgres;

	@Test
	void appConnectsAsLeastPrivilegeUser() {
		var jdbc = new JdbcTemplate(dataSource);
		assertThat(jdbc.queryForObject("select current_user", String.class)).isEqualTo("ehr_app");
	}

	@Test
	void appCannotCreateTables() {
		var jdbc = new JdbcTemplate(dataSource);
		assertThatThrownBy(() -> jdbc.execute("create table rogue (id int)"))
			.rootCause()
			.hasMessageContaining("permission denied");
	}

	@Test
	void ownerTablesGrantAppReadWriteButNotDelete() throws SQLException {
		try (Connection owner = DriverManager.getConnection(postgres.getJdbcUrl(), "ehr_owner", "ehr_owner");
				Statement st = owner.createStatement()) {
			st.execute("create table if not exists role_probe (id bigint generated always as identity)");
		}
		var jdbc = new JdbcTemplate(dataSource);
		String privileges = """
				select has_table_privilege('role_probe', 'SELECT') as select,
				       has_table_privilege('role_probe', 'INSERT') as insert,
				       has_table_privilege('role_probe', 'UPDATE') as update,
				       has_table_privilege('role_probe', 'DELETE') as delete,
				       has_table_privilege('role_probe', 'TRUNCATE') as truncate
				""";
		assertThat(jdbc.queryForList(privileges).getFirst().values()).containsExactly(true, true, true, false, false);
		jdbc.update("insert into role_probe default values");
	}

	@Test
	void appCannotEditMigrationHistory() {
		var jdbc = new JdbcTemplate(dataSource);
		assertThatThrownBy(() -> jdbc.update("update flyway_schema_history set success = false"))
			.rootCause()
			.hasMessageContaining("permission denied");
	}

}
