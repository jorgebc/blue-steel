package com.bluesteel.application.model.session;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Discriminated union of diff review cards surfaced to the user after session ingestion. The {@code
 * cardType} property drives Jackson polymorphic serialization and the frontend card renderer.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "cardType")
@JsonSubTypes({
  @JsonSubTypes.Type(value = ExistingEntityCard.class, name = "EXISTING"),
  @JsonSubTypes.Type(value = NewEntityCard.class, name = "NEW"),
  @JsonSubTypes.Type(value = UncertainEntityCard.class, name = "UNCERTAIN")
})
public sealed interface DiffCard permits ExistingEntityCard, NewEntityCard, UncertainEntityCard {}
