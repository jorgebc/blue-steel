package com.bluesteel.adapters.out.health;

import com.bluesteel.application.port.out.ComponentStatus;
import com.bluesteel.application.port.out.HealthPort;
import com.bluesteel.application.port.out.SystemHealth;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Component
public class SystemHealthAdapter implements HealthPort {

  @Nullable private final JdbcTemplate jdbcTemplate;

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
