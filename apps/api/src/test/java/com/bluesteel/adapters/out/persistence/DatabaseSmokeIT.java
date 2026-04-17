package com.bluesteel.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.bluesteel.TestcontainersPostgresBaseIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Smoke integration test: verifies the Testcontainers PostgreSQL + pgvector container starts
 * cleanly and Liquibase migrations apply without error (D-056).
 */
@DisplayName("Database smoke test")
class DatabaseSmokeIT extends TestcontainersPostgresBaseIT {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("database connection is alive")
  void databaseConnectionAlive() {
    Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);

    assertThat(result).isEqualTo(1);
  }

  @Test
  @DisplayName("pgvector extension is loaded")
  void pgvectorExtensionLoaded() {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM pg_extension WHERE extname = 'vector'", Long.class);

    assertThat(count).isEqualTo(1L);
  }
}
