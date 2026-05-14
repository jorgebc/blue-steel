package com.bluesteel.adapters.in.web;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS configuration. The production frontend origin is read from {@code VITE_API_BASE_URL}. The
 * local dev origin ({@code http://localhost:5173}) is only added when running under the {@code
 * local} Spring profile so it is never active in production.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  /** Production frontend origin. Expected to be set in prod; may be blank in local dev. */
  @Value("${VITE_API_BASE_URL:}")
  private String corsAllowedOrigin;

  /**
   * {@code true} when the {@code local} profile is active; controls whether the local dev origin is
   * added to the allowlist.
   */
  @Value("${spring.profiles.active:}")
  private String activeProfiles;

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    List<String> origins = new ArrayList<>();

    // Only allow localhost in local dev profile — never expose it in production
    if (activeProfiles.contains("local")) {
      origins.add("http://localhost:5173");
    }
    if (!corsAllowedOrigin.isBlank()) {
      origins.add(corsAllowedOrigin);
    }

    if (origins.isEmpty()) {
      // No origins configured: refuse all cross-origin requests (safe default)
      return;
    }

    registry
        .addMapping("/api/**")
        .allowedOrigins(origins.toArray(String[]::new))
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        // Restrict to the headers the API actually uses rather than wildcard
        .allowedHeaders("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With")
        .allowCredentials(true);
  }
}
