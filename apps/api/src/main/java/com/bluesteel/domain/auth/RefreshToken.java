package com.bluesteel.domain.auth;

import com.bluesteel.domain.exception.DomainException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

/** Rotating refresh token with family-based reuse detection (D-059). Raw token never stored. */
public class RefreshToken {

  private final UUID id;
  private final UUID userId;
  private final UUID familyId;
  private final String tokenHash;
  private final RefreshTokenStatus status;
  private final Instant expiresAt;
  private final Instant createdAt;

  private RefreshToken(
      UUID id,
      UUID userId,
      UUID familyId,
      String tokenHash,
      RefreshTokenStatus status,
      Instant expiresAt,
      Instant createdAt) {
    this.id = id;
    this.userId = userId;
    this.familyId = familyId;
    this.tokenHash = tokenHash;
    this.status = status;
    this.expiresAt = expiresAt;
    this.createdAt = createdAt;
  }

  public static RefreshToken create(UUID userId, UUID familyId, String rawToken) {
    return new RefreshToken(
        UUID.randomUUID(),
        userId,
        familyId,
        sha256(rawToken),
        RefreshTokenStatus.ACTIVE,
        Instant.now().plus(30, ChronoUnit.DAYS),
        Instant.now());
  }

  /** Reconstitutes a persisted refresh token. */
  public static RefreshToken reconstitute(
      UUID id,
      UUID userId,
      UUID familyId,
      String tokenHash,
      RefreshTokenStatus status,
      Instant expiresAt,
      Instant createdAt) {
    return new RefreshToken(id, userId, familyId, tokenHash, status, expiresAt, createdAt);
  }

  /** Transitions ACTIVE → CONSUMED. Throws if already consumed/revoked or expired. */
  public RefreshToken consume() {
    if (this.status != RefreshTokenStatus.ACTIVE) {
      throw new DomainException("Cannot consume a non-active refresh token");
    }
    if (Instant.now().isAfter(this.expiresAt)) {
      throw new DomainException("Refresh token has expired");
    }
    return new RefreshToken(
        id, userId, familyId, tokenHash, RefreshTokenStatus.CONSUMED, expiresAt, createdAt);
  }

  public boolean isExpired() {
    return Instant.now().isAfter(expiresAt);
  }

  public UUID id() {
    return id;
  }

  public UUID userId() {
    return userId;
  }

  public UUID familyId() {
    return familyId;
  }

  public String tokenHash() {
    return tokenHash;
  }

  public RefreshTokenStatus status() {
    return status;
  }

  public Instant expiresAt() {
    return expiresAt;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public static String sha256(String raw) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(bytes);
    } catch (NoSuchAlgorithmException e) {
      throw new DomainException("SHA-256 not available");
    }
  }
}
