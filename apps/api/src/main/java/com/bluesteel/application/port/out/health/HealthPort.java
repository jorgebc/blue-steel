package com.bluesteel.application.port.out.health;

import com.bluesteel.application.model.health.SystemHealth;

/**
 * Driven port: returns a snapshot of overall system health. Implementations aggregate individual
 * component checks (DB, external services) and compute the {@link
 * com.bluesteel.application.model.health.OverallStatus}.
 */
public interface HealthPort {

  /** Runs all component health checks and returns the aggregated result. */
  SystemHealth check();
}
