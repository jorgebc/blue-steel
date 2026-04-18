package com.bluesteel.adapters.in.web.health;

import com.bluesteel.adapters.in.web.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

  @GetMapping
  public ResponseEntity<ApiResponse<HealthResponse>> health() {
    return ResponseEntity.ok(ApiResponse.success(new HealthResponse("UP")));
  }
}
