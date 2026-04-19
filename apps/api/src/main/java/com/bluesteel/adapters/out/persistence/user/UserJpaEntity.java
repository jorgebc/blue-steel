package com.bluesteel.adapters.out.persistence.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
class UserJpaEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "email", nullable = false, unique = true)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(name = "is_admin", nullable = false)
  private boolean isAdmin;

  @Column(name = "force_password_change", nullable = false)
  private boolean forcePasswordChange;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected UserJpaEntity() {}

  UserJpaEntity(
      UUID id,
      String email,
      String passwordHash,
      boolean isAdmin,
      boolean forcePasswordChange,
      Instant createdAt) {
    this.id = id;
    this.email = email;
    this.passwordHash = passwordHash;
    this.isAdmin = isAdmin;
    this.forcePasswordChange = forcePasswordChange;
    this.createdAt = createdAt;
  }

  UUID getId() {
    return id;
  }

  String getEmail() {
    return email;
  }

  String getPasswordHash() {
    return passwordHash;
  }

  boolean isAdmin() {
    return isAdmin;
  }

  boolean isForcePasswordChange() {
    return forcePasswordChange;
  }

  Instant getCreatedAt() {
    return createdAt;
  }
}
