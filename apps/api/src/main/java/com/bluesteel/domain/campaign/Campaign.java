package com.bluesteel.domain.campaign;

import java.time.Instant;
import java.util.UUID;

/** Campaign aggregate — represents a tabletop RPG campaign. */
public class Campaign {

  private final UUID id;
  private final String name;
  private final UUID createdBy;
  private final Instant createdAt;

  private Campaign(UUID id, String name, UUID createdBy, Instant createdAt) {
    if (name == null || name.isBlank())
      throw new IllegalArgumentException("Name must not be blank");
    this.id = id;
    this.name = name;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
  }

  public static Campaign create(UUID id, String name, UUID createdBy, Instant createdAt) {
    return new Campaign(id, name, createdBy, createdAt);
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
}
