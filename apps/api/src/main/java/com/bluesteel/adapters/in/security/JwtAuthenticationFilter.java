package com.bluesteel.adapters.in.security;

import com.bluesteel.application.model.auth.JwtClaims;
import com.bluesteel.domain.exception.JwtValidationException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Extracts and validates the {@code Authorization: Bearer} JWT on every request. On success,
 * populates the {@code SecurityContext}; on failure, passes the request unauthenticated and Spring
 * Security returns 401 for protected routes.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  @Value("${jwt.secret}")
  private String jwtSecret;

  private byte[] secretBytes;

  @PostConstruct
  void init() {
    this.secretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      String token = header.substring(7);
      try {
        JwtClaims claims = validate(token);
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

  private JwtClaims validate(String token) {
    try {
      SignedJWT jwt = SignedJWT.parse(token);
      if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())) {
        throw new JwtValidationException("Unexpected JWT algorithm");
      }
      JWSVerifier verifier = new MACVerifier(secretBytes);
      if (!jwt.verify(verifier)) {
        throw new JwtValidationException("JWT signature verification failed");
      }

      JWTClaimsSet claims = jwt.getJWTClaimsSet();
      Date expirationTime = claims.getExpirationTime();
      if (expirationTime == null || expirationTime.before(new Date())) {
        throw new JwtValidationException("JWT has expired");
      }

      UUID userId = UUID.fromString(claims.getStringClaim("user_id"));
      boolean isAdmin = Boolean.TRUE.equals(claims.getBooleanClaim("is_admin"));
      Instant expiresAt = expirationTime.toInstant();
      return new JwtClaims(userId, isAdmin, expiresAt);
    } catch (ParseException | JOSEException e) {
      throw new JwtValidationException("Invalid JWT: " + e.getMessage(), e);
    }
  }
}
