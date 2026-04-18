package com.bluesteel.adapters.in.web.health;

import com.bluesteel.adapters.in.web.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

  private final JdbcTemplate jdbcTemplate;

  public HealthController(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @GetMapping
  public ResponseEntity<ApiResponse<HealthResponse>> health() {
    String dbStatus = checkDb();
    String overallStatus = "UP".equals(dbStatus) ? "UP" : "DEGRADED";
    return ResponseEntity.ok(ApiResponse.success(new HealthResponse(overallStatus, dbStatus)));
  }

  private String checkDb() {
    try {
      jdbcTemplate.queryForObject("SELECT 1", Integer.class);
      return "UP";
    } catch (Exception e) {
      return "DOWN";
    }
  }
}
