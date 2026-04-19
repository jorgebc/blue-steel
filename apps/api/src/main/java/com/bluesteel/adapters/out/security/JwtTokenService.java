package com.bluesteel.adapters.out.security;

import com.bluesteel.application.model.auth.JwtClaims;
import com.bluesteel.application.port.out.auth.JwtPort;
import com.bluesteel.domain.exception.JwtValidationException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** HS256 JWT implementation using nimbus-jose-jwt (D-060). */
@Component
public class JwtTokenService implements JwtPort {

  private static final String CLAIM_USER_ID = "user_id";
  private static final String CLAIM_IS_ADMIN = "is_admin";

  @Value("${jwt.secret}")
  private String jwtSecret;

  private byte[] secretBytes;

  @PostConstruct
  void init() {
    byte[] bytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
    if (bytes.length < 32) {
      throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
    }
    this.secretBytes = bytes;
  }

  @Override
  public String issue(UUID userId, boolean isAdmin, Duration ttl) {
    try {
      Instant now = Instant.now();
      Instant expiry = now.plus(ttl);

      JWTClaimsSet claims =
          new JWTClaimsSet.Builder()
              .claim(CLAIM_USER_ID, userId.toString())
              .claim(CLAIM_IS_ADMIN, isAdmin)
              .issueTime(Date.from(now))
              .expirationTime(Date.from(expiry))
              .build();

      SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
      JWSSigner signer = new MACSigner(secretBytes);
      jwt.sign(signer);
      return jwt.serialize();
    } catch (JOSEException e) {
      throw new JwtValidationException("Failed to sign JWT", e);
    }
  }

  @Override
  public JwtClaims validate(String token) {
    try {
      SignedJWT jwt = SignedJWT.parse(token);
      JWSVerifier verifier = new MACVerifier(secretBytes);
      if (!jwt.verify(verifier)) {
        throw new JwtValidationException("JWT signature verification failed");
      }

      JWTClaimsSet claims = jwt.getJWTClaimsSet();
      Date expirationTime = claims.getExpirationTime();
      if (expirationTime == null || expirationTime.before(new Date())) {
        throw new JwtValidationException("JWT has expired");
      }

      UUID userId = UUID.fromString(claims.getStringClaim(CLAIM_USER_ID));
      boolean isAdmin = Boolean.TRUE.equals(claims.getBooleanClaim(CLAIM_IS_ADMIN));
      return new JwtClaims(userId, isAdmin, expirationTime.toInstant());
    } catch (ParseException | JOSEException e) {
      throw new JwtValidationException("Invalid JWT: " + e.getMessage(), e);
    }
  }
}
