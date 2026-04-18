package com.bluesteel;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for all persistence integration tests that require a live PostgreSQL + pgvector
 * database (D-056).
 *
 * <p>Starts a shared {@code pgvector/pgvector:pg16} container once per test class, applies
 * Liquibase migrations on Spring context boot, and wires the container's JDBC URL into the Spring
 * DataSource via {@link DynamicPropertySource}.
 *
 * <p>Subclasses inherit {@link #dataSource} and may inject additional Spring beans as needed.
 */
@Testcontainers
@SpringBootTest(
    classes = BlueSteelApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      // Re-enable DataSource, JPA, and Liquibase for persistence ITs
      // (excluded in application.yml until F1.3 adds PersistenceConfig)
      "spring.autoconfigure.exclude=",
      "spring.liquibase.enabled=true",
      "spring.jpa.hibernate.ddl-auto=none"
    })
public abstract class TestcontainersPostgresBaseIT {

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("pgvector/pgvector:pg16")
          .withDatabaseName("bluesteel_test")
          .withUsername("test")
          .withPassword("test");

  @DynamicPropertySource
  static void configureDataSource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired protected DataSource dataSource;
}
