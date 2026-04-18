package com.bluesteel.adapters.out.persistence;

import org.springframework.context.annotation.Configuration;

/**
 * Persistence layer configuration anchor. DataSource is autoconfigured from {@code DATABASE_URL}
 * via {@code spring.datasource.url}. Hibernate uses {@code CamelCaseToUnderscoresNamingStrategy}
 * (Spring Boot default) and {@code ddl-auto=validate} — Liquibase owns schema evolution.
 */
@Configuration
public class PersistenceConfig {}
