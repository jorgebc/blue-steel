package com.bluesteel.adapters.in.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;

/**
 * Stateless JWT filter chain. Public routes: health, auth. All others require a valid JWT. Also
 * enforces HSTS, content type options, frame options, and referrer policy headers.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(
                    (request, response, e) ->
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/v1/health", "/api/v1/auth/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
        // Security response headers (OWASP A05 — Security Misconfiguration)
        .headers(
            headers ->
                headers
                    // HSTS: require HTTPS for 1 year, include subdomains
                    .httpStrictTransportSecurity(
                        hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                    // Prevent MIME-type sniffing
                    .contentTypeOptions(ct -> {})
                    // Disallow framing (clickjacking protection)
                    .frameOptions(frame -> frame.deny())
                    // Disable legacy XSS filter (modern browsers ignore it; can cause issues)
                    .xssProtection(
                        xss -> xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.DISABLED))
                    // Referrer-Policy: only send origin on same-origin requests
                    .referrerPolicy(
                        ref -> ref.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                    // Permissions-Policy: restrict access to sensitive browser APIs
                    .permissionsPolicy(pp -> pp.policy("camera=(), microphone=(), geolocation=()")))
        .build();
  }
}
