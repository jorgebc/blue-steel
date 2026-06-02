package com.bluesteel.application.model.worldstate;

/**
 * Optional filters for an entity list query. Both fields are nullable; a {@code null} field imposes
 * no constraint.
 */
public record EntityListFilter(String nameContains, String status) {

  /** A filter that imposes no constraints. */
  public static EntityListFilter none() {
    return new EntityListFilter(null, null);
  }
}
