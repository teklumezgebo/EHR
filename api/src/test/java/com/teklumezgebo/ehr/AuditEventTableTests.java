package com.teklumezgebo.ehr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Verifies audit_event is append-only and that each insert extends the hash chain. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AuditEventTableTests {

	@Autowired
	DataSource dataSource;

	@Autowired
	PostgreSQLContainer postgres;

	private long insertEvent(JdbcTemplate jdbc) {
		return jdbc.queryForObject("""
				insert into audit_event (id, at, user_id, action, outcome, resource_type, resource_id, hash)
				values (0, 'epoch', ?, 'READ', 'SUCCESS', 'Patient', 'p1', '\\x00')
				returning id
				""", Long.class, UUID.randomUUID());
	}

	@Test
	void insertsChainToThePreviousRowAndIgnoreCallerValues() {
		var jdbc = new JdbcTemplate(dataSource);
		long first = insertEvent(jdbc);
		long second = insertEvent(jdbc);

		assertThat(second).isEqualTo(first + 1);
		assertThat(jdbc.queryForObject(
				"select b.prev_hash = a.hash from audit_event a, audit_event b where a.id = ? and b.id = ?",
				Boolean.class, first, second)).isTrue();
		assertThat(jdbc.queryForObject(
				"select at > now() - interval '1 minute' and hash = audit_event_hash(prev_hash, e) from audit_event e where id = ?",
				Boolean.class, second)).isTrue();
	}

	@Test
	void appCannotUpdateOrDelete() {
		var jdbc = new JdbcTemplate(dataSource);
		long id = insertEvent(jdbc);

		assertThatThrownBy(() -> jdbc.update("update audit_event set action = 'X' where id = ?", id)).rootCause()
			.hasMessageContaining("permission denied");
		assertThatThrownBy(() -> jdbc.update("delete from audit_event where id = ?", id)).rootCause()
			.hasMessageContaining("permission denied");
	}

	@Test
	void ownerCannotUpdateDeleteOrTruncate() throws SQLException {
		long id = insertEvent(new JdbcTemplate(dataSource));
		try (Connection owner = DriverManager.getConnection(postgres.getJdbcUrl(), "ehr_owner", "ehr_owner");
				Statement st = owner.createStatement()) {
			for (String sql : new String[] { "update audit_event set action = 'X' where id = " + id,
					"delete from audit_event where id = " + id, "truncate audit_event" }) {
				assertThatThrownBy(() -> st.execute(sql)).hasMessageContaining("append-only");
			}
		}
	}

}
