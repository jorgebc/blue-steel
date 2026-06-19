package com.bluesteel.domain.user;

import java.time.Instant;
import java.util.UUID;

/** Platform user — the sole authentication principal in the system. */
public class User {

  /** Default UI locale applied when none is set (D-101). */
  private static final String DEFAULT_UI_LOCALE = "en";

  /** Default theme applied when none is set (D-101). */
  private static final String DEFAULT_THEME = "system";

  private final UUID id;
  private final String email;
  private final String passwordHash;
  private final boolean isAdmin;
  private final boolean forcePasswordChange;
  private final Instant createdAt;
  private final String displayName;
  private final String avatarAccentColor;
  private final String uiLocale;
  private final String theme;

  private User(
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
    this.displayName = displayName;
    this.avatarAccentColor = avatarAccentColor;
    this.uiLocale = uiLocale;
    this.theme = theme;
  }

  /** Creates a user with default profile/settings (no display name or accent; en/system). */
  public static User create(
      UUID id,
      String email,
      String passwordHash,
      boolean isAdmin,
      boolean forcePasswordChange,
      Instant createdAt) {
    return new User(
        id,
        email,
        passwordHash,
        isAdmin,
        forcePasswordChange,
        createdAt,
        null,
        null,
        DEFAULT_UI_LOCALE,
        DEFAULT_THEME);
  }

  /** Creates a user carrying explicit profile/settings values (used when loading from storage). */
  public static User create(
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
    return new User(
        id,
        email,
        passwordHash,
        isAdmin,
        forcePasswordChange,
        createdAt,
        displayName,
        avatarAccentColor,
        uiLocale,
        theme);
  }

  /** Returns a new User with an updated password hash and forcePasswordChange cleared. */
  public User withUpdatedPassword(String newPasswordHash) {
    return new User(
        id,
        email,
        newPasswordHash,
        isAdmin,
        false,
        createdAt,
        displayName,
        avatarAccentColor,
        uiLocale,
        theme);
  }

  /** Returns a new User with a fresh invitation password hash and forcePasswordChange set. */
  public User withRefreshedInvitation(String newPasswordHash) {
    return new User(
        id,
        email,
        newPasswordHash,
        isAdmin,
        true,
        createdAt,
        displayName,
        avatarAccentColor,
        uiLocale,
        theme);
  }

  /** Returns a new User with the profile/settings fields replaced; identity fields untouched. */
  public User withUpdatedProfile(
      String displayName, String avatarAccentColor, String uiLocale, String theme) {
    return new User(
        id,
        email,
        passwordHash,
        isAdmin,
        forcePasswordChange,
        createdAt,
        displayName,
        avatarAccentColor,
        uiLocale,
        theme);
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

  public String displayName() {
    return displayName;
  }

  public String avatarAccentColor() {
    return avatarAccentColor;
  }

  public String uiLocale() {
    return uiLocale;
  }

  public String theme() {
    return theme;
  }
}
