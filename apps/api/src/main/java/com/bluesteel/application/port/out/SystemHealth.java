package com.bluesteel.application.port.out;

/**
 * Snapshot of overall system health. Add a new field per component as the system grows; update
 * {@link #of} to incorporate it into the {@link OverallStatus} computation.
 */
public record SystemHealth(OverallStatus overall, ComponentStatus db) {

  /**
   * Creates a {@code SystemHealth} from component statuses, computing the overall status: {@code
   * UP} only when all components are {@code UP}, {@code DEGRADED} otherwise.
   */
  public static SystemHealth of(ComponentStatus db) {
    OverallStatus overall = db == ComponentStatus.UP ? OverallStatus.UP : OverallStatus.DEGRADED;
    return new SystemHealth(overall, db);
  }
}
