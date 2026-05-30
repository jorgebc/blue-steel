package com.bluesteel.application.model.commit;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Commit decision action for a diff card. Serializes as lowercase JSON string. */
public enum CardAction {
  ACCEPT,
  EDIT,
  DELETE;

  @JsonValue
  public String toJson() {
    return name().toLowerCase();
  }

  @JsonCreator
  public static CardAction fromJson(String value) {
    return valueOf(value.toUpperCase());
  }
}
