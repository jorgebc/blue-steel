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

  @Column(name = "display_name")
  private String displayName;

  @Column(name = "avatar_accent_color")
  private String avatarAccentColor;

  @Column(name = "ui_locale", nullable = false)
  private String uiLocale;

  @Column(name = "theme", nullable = false)
  private String theme;

  protected UserJpaEntity() {}

  UserJpaEntity(
      UUID id,
      String email,
      String passwordHash,
      boolean isAdmin,
      boolean forcePasswordChange,
      Instant createdAt,
      String displayName,
      String avatarAccentColor,
      String uiLocale,
      String theme) {
    this.id = id;
    this.email = email;
    this.passwordHash = passwordHash;
    this.isAdmin = isAdmin;
    this.forcePasswordChange = forcePasswordChange;
    this.createdAt = createdAt;
    this.displayName = displayName;
    this.avatarAccentColor = avatarAccentColor;
    this.uiLocale = uiLocale;
    this.theme = theme;
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

  String getDisplayName() {
    return displayName;
  }

  String getAvatarAccentColor() {
    return avatarAccentColor;
  }

  String getUiLocale() {
    return uiLocale;
  }

  String getTheme() {
    return theme;
  }
}
