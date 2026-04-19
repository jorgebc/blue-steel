package com.bluesteel.application.service.health;

import com.bluesteel.application.model.health.SystemHealth;
import com.bluesteel.application.port.in.health.CheckHealthUseCase;
import com.bluesteel.application.port.out.health.HealthPort;
import org.springframework.stereotype.Service;

/** Orchestrates system health checks by delegating to each driven infrastructure port. */
@Service
public class HealthService implements CheckHealthUseCase {

  private final HealthPort healthPort;

  public HealthService(HealthPort healthPort) {
    this.healthPort = healthPort;
  }

  @Override
  public SystemHealth check() {
    return healthPort.check();
  }
}
