package com.bluesteel.application.port.out;

public record SystemHealth(OverallStatus overall, ComponentStatus db) {

  public static SystemHealth of(ComponentStatus db) {
    OverallStatus overall = db == ComponentStatus.UP ? OverallStatus.UP : OverallStatus.DEGRADED;
    return new SystemHealth(overall, db);
  }
}
