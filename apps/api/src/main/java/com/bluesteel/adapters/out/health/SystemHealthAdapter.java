package com.bluesteel.adapters.out.health;

import com.bluesteel.application.model.health.ComponentStatus;
import com.bluesteel.application.model.health.SystemHealth;
import com.bluesteel.application.port.out.health.HealthPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Aggregates infrastructure health checks and exposes them via {@link HealthPort}. Add a new
 * private check method and include it in {@link #check()} when a new component is introduced.
 * JdbcTemplate is optional so the adapter registers safely when the datasource is absent (e.g.
 * web-layer tests), in which case DB is reported as {@link ComponentStatus#DOWN}.
 */
@Component
public class SystemHealthAdapter implements HealthPort {

  private final JdbcTemplate jdbcTemplate;

  public SystemHealthAdapter(@Autowired(required = false) JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public SystemHealth check() {
    return SystemHealth.of(checkDb());
  }

  private ComponentStatus checkDb() {
    if (jdbcTemplate == null) {
      return ComponentStatus.DOWN;
    }
    try {
      jdbcTemplate.queryForObject("SELECT 1", Integer.class);
      return ComponentStatus.UP;
    } catch (Exception e) {
      return ComponentStatus.DOWN;
    }
  }
}
