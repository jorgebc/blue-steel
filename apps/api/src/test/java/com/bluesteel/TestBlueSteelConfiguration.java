package com.bluesteel;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;

/**
 * Minimal Spring Boot configuration for integration tests.
 *
 * <p>Used as the bootstrap configuration by {@link TestcontainersPostgresBaseIT} (via {@link
 * org.springframework.boot.test.context.SpringBootTest}) until the production {@code
 * BlueSteelApplication} is introduced in F1.2.
 *
 * <p>Security auto-configuration is excluded because the Security filter chain requires beans (JWT
 * filter, SecurityConfig) that are not yet present in F1.1.
 */
@SpringBootConfiguration
@EnableAutoConfiguration(
    exclude = {SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class})
public class TestBlueSteelConfiguration {}
