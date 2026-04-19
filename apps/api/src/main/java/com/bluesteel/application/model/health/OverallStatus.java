package com.bluesteel.application.model.health;

/**
 * Aggregate health status of the application. {@code DEGRADED} means at least one component is down
 * but the application is still running.
 */
public enum OverallStatus {
  UP,
  DEGRADED
}
