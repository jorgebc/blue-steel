package com.bluesteel.domain.campaign;

import java.time.Instant;
import java.util.UUID;

/** Campaign aggregate — represents a tabletop RPG campaign. */
public class Campaign {

  /** Default content language applied when none is supplied; fixed at creation (D-103). */
  private static final String DEFAULT_CONTENT_LANGUAGE = "en";

  private final UUID id;
  private final String name;
  private final UUID createdBy;
  private final Instant createdAt;
  private final String contentLanguage;

  private Campaign(
      UUID id, String name, UUID createdBy, Instant createdAt, String contentLanguage) {
    if (name == null || name.isBlank())
      throw new IllegalArgumentException("Name must not be blank");
    this.id = id;
    this.name = name;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.contentLanguage =
        (contentLanguage == null || contentLanguage.isBlank())
            ? DEFAULT_CONTENT_LANGUAGE
            : contentLanguage;
  }

  /** Creates a campaign with the default content language ({@code en}). */
  public static Campaign create(UUID id, String name, UUID createdBy, Instant createdAt) {
    return new Campaign(id, name, createdBy, createdAt, DEFAULT_CONTENT_LANGUAGE);
  }

  /** Creates a campaign with an explicit content language; null/blank falls back to {@code en}. */
  public static Campaign create(
      UUID id, String name, UUID createdBy, Instant createdAt, String contentLanguage) {
    return new Campaign(id, name, createdBy, createdAt, contentLanguage);
  }

  public UUID id() {
    return id;
  }

  public String name() {
    return name;
  }

  public UUID createdBy() {
    return createdBy;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public String contentLanguage() {
    return contentLanguage;
  }
}
