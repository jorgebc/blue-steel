package com.bluesteel.domain.user;

import java.time.Instant;
import java.util.UUID;

/** Platform user — the sole authentication principal in the system. */
public class User {

  private final UUID id;
  private final String email;
  private final String passwordHash;
  private final boolean isAdmin;
  private final boolean forcePasswordChange;
  private final Instant createdAt;

  private User(
      UUID id,
      String email,
      String passwordHash,
      boolean isAdmin,
      boolean forcePasswordChange,
      Instant createdAt) {
    if (email == null || email.isBlank())
      throw new IllegalArgumentException("Email must not be blank");
    if (passwordHash == null || passwordHash.isBlank())
      throw new IllegalArgumentException("Password hash must not be blank");
    this.id = id;
    this.email = email;
    this.passwordHash = passwordHash;
    this.isAdmin = isAdmin;
    this.forcePasswordChange = forcePasswordChange;
    this.createdAt = createdAt;
  }

  public static User create(
      UUID id,
      String email,
      String passwordHash,
      boolean isAdmin,
      boolean forcePasswordChange,
      Instant createdAt) {
    return new User(id, email, passwordHash, isAdmin, forcePasswordChange, createdAt);
  }

  /** Returns a new User with an updated password hash and forcePasswordChange cleared. */
  public User withUpdatedPassword(String newPasswordHash) {
    return new User(id, email, newPasswordHash, isAdmin, false, createdAt);
  }

  /** Returns a new User with a fresh invitation password hash and forcePasswordChange set. */
  public User withRefreshedInvitation(String newPasswordHash) {
    return new User(id, email, newPasswordHash, isAdmin, true, createdAt);
  }

  public UUID id() {
    return id;
  }

  public String email() {
    return email;
  }

  public String passwordHash() {
    return passwordHash;
  }

  public boolean isAdmin() {
    return isAdmin;
  }

  public boolean forcePasswordChange() {
    return forcePasswordChange;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
