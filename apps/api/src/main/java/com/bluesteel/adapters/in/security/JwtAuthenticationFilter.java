package com.bluesteel.adapters.in.security;

import com.bluesteel.application.model.auth.JwtClaims;
import com.bluesteel.application.port.in.auth.JwtValidationPort;
import com.bluesteel.domain.exception.JwtValidationException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Extracts and validates the {@code Authorization: Bearer} JWT on every request. Delegates
 * validation to {@link JwtValidationPort} to keep the algorithm and key logic in one place. On
 * success, populates the {@code SecurityContext}; on failure, passes the request unauthenticated
 * and Spring Security returns 401 for protected routes.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtValidationPort jwtValidationPort;

  public JwtAuthenticationFilter(JwtValidationPort jwtValidationPort) {
    this.jwtValidationPort = jwtValidationPort;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      String token = header.substring(7);
      try {
        JwtClaims claims = jwtValidationPort.validate(token);
        List<SimpleGrantedAuthority> authorities =
            claims.isAdmin()
                ? List.of(
                    new SimpleGrantedAuthority("ROLE_ADMIN"),
                    new SimpleGrantedAuthority("ROLE_USER"))
                : List.of(new SimpleGrantedAuthority("ROLE_USER"));
        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(claims.userId().toString(), null, authorities);
        auth.setDetails(claims);
        SecurityContextHolder.getContext().setAuthentication(auth);
      } catch (JwtValidationException e) {
        // Do not set SecurityContext — Spring Security returns 401 for protected routes
      }
    }
    chain.doFilter(request, response);
  }
}
