package com.bluesteel.application.port.out;

/**
 * Driven port: returns a snapshot of overall system health. Implementations aggregate individual
 * component checks (DB, external services) and compute the {@link OverallStatus}.
 */
public interface HealthPort {

  /** Runs all component health checks and returns the aggregated result. */
  SystemHealth check();
}
