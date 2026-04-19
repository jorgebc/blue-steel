package com.bluesteel.application.port.in.health;

import com.bluesteel.application.model.health.SystemHealth;

/** Driving port: queries the health status of all system components. */
public interface CheckHealthUseCase {

  /** Returns a snapshot of current system health. Never throws; DOWN components are reported. */
  SystemHealth check();
}
