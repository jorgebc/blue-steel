package com.bluesteel.adapters.out.persistence;

import org.springframework.context.annotation.Configuration;

@Configuration
public class PersistenceConfig {
  // DataSource: autoconfigured from spring.datasource.url (DATABASE_URL env var)
  // Naming: CamelCaseToUnderscoresNamingStrategy (Spring Boot default → snake_case columns)
  // DDL: validate — Liquibase owns the schema; Hibernate only validates it
  // JPA repositories and transaction management: enabled by Spring Boot autoconfiguration
}
