package com.bluesteel.adapters.in.web;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS configuration. Allowed origins are read from {@code cors.allowed-origins} — set per Spring
 * profile in the properties files. No profile detection in this class.
 */
@Configuration
public class WebConfig {

  @Value("${cors.allowed-origins:}")
  private String allowedOrigins;

  /**
   * Exposes CORS policy as a bean so Spring Security's filter can apply it before the authorization
   * check, ensuring OPTIONS preflights receive the correct headers without a JWT.
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    if (allowedOrigins.isBlank()) {
      return request -> null;
    }
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(
        Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .toList());
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(
        List.of("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With"));
    config.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", config);
    return source;
  }
}
