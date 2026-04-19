package com.bluesteel.adapters.in.web.health;

import com.bluesteel.adapters.in.web.ApiResponse;
import com.bluesteel.application.model.health.SystemHealth;
import com.bluesteel.application.port.in.health.CheckHealthUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes {@code GET /api/v1/health} — the only unauthenticated endpoint in the API. */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

  private final CheckHealthUseCase checkHealthUseCase;

  public HealthController(CheckHealthUseCase checkHealthUseCase) {
    this.checkHealthUseCase = checkHealthUseCase;
  }

  @GetMapping
  public ResponseEntity<ApiResponse<HealthResponse>> health() {
    SystemHealth health = checkHealthUseCase.check();
    return ResponseEntity.ok(
        ApiResponse.success(new HealthResponse(health.overall(), health.db())));
  }
}
