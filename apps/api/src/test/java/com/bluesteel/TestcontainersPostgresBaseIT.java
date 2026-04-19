package com.bluesteel;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for all persistence integration tests that require a live PostgreSQL + pgvector
 * database (D-056).
 *
 * <p>Uses the Testcontainers singleton pattern: the container is started once in a static
 * initializer and stays alive for the entire test suite JVM. This prevents Spring's
 * ApplicationContext cache from reconnecting to a stopped container when multiple subclasses share
 * the same cached context.
 *
 * <p>Subclasses inherit {@link #dataSource} and may inject additional Spring beans as needed.
 */
@SpringBootTest(
    classes = BlueSteelApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      // Prevent Hibernate from attempting schema validation — Liquibase owns the schema
      "spring.jpa.hibernate.ddl-auto=none"
    })
public abstract class TestcontainersPostgresBaseIT {

  static final PostgreSQLContainer<?> postgres;

  static {
    postgres =
        new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("bluesteel_test")
            .withUsername("test")
            .withPassword("test");
    postgres.start();
  }

  @DynamicPropertySource
  static void configureDataSource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired protected DataSource dataSource;
}
