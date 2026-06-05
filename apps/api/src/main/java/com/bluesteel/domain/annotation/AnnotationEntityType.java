package com.bluesteel.domain.annotation;

/** Valid entity types that can be annotated (mirrors the DB CHECK constraint in 0017). */
public enum AnnotationEntityType {
  actor,
  space,
  event,
  relation;

  /** Returns the lowercase string representation used in the database and HTTP API. */
  public String value() {
    return name();
  }
}
