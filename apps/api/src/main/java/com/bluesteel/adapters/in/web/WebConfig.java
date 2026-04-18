package com.bluesteel.adapters.in.web;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  @Value("${VITE_API_BASE_URL:}")
  private String corsAllowedOrigin;

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    List<String> origins = new ArrayList<>();
    origins.add("http://localhost:5173");
    if (!corsAllowedOrigin.isBlank()) {
      origins.add(corsAllowedOrigin);
    }

    registry
        .addMapping("/api/**")
        .allowedOrigins(origins.toArray(String[]::new))
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .allowCredentials(true);
  }
}
