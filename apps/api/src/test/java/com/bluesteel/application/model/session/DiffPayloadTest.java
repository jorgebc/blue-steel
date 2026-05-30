package com.bluesteel.application.model.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DiffPayload — full Jackson round-trip")
class DiffPayloadTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
  }

  @Test
  @DisplayName("should round-trip a payload containing all three card variants and a ConflictCard")
  void diffPayload_withMixedCardsAndConflict_roundTripsThroughJackson() throws Exception {
    UUID existingCardId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UUID entityId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    UUID newCardId = UUID.fromString("33333333-3333-3333-3333-333333333333");
    UUID uncertainCardId = UUID.fromString("44444444-4444-4444-4444-444444444444");
    UUID conflictId = UUID.fromString("55555555-5555-5555-5555-555555555555");
    UUID conflictEntityId = UUID.fromString("66666666-6666-6666-6666-666666666666");

    ExistingEntityCard existingCard =
        new ExistingEntityCard(
            existingCardId, entityId, "actor", "Mira", Map.of("description", "Changed"));
    NewEntityCard newCard =
        new NewEntityCard(
            newCardId,
            "space",
            "The Fortress",
            Map.of("name", "The Fortress", "description", "New location"));
    UncertainEntityCard uncertainCard =
        new UncertainEntityCard(uncertainCardId, "event", "The Battle", null);
    ConflictCard conflictCard =
        new ConflictCard(
            conflictId,
            conflictEntityId,
            "actor",
            "Conflict in timeline",
            "Mira is dead",
            "Mira is alive");

    DiffPayload original =
        new DiffPayload(
            "Heroes stormed the fortress and confronted the Shadow.",
            List.of(existingCard),
            List.of(newCard),
            List.of(uncertainCard),
            List.of(),
            List.of(conflictCard));

    String json = objectMapper.writeValueAsString(original);
    DiffPayload deserialized = objectMapper.readValue(json, DiffPayload.class);

    assertThat(deserialized.narrativeSummaryHeader())
        .isEqualTo("Heroes stormed the fortress and confronted the Shadow.");

    assertThat(deserialized.actors()).hasSize(1);
    assertThat(deserialized.actors().get(0)).isInstanceOf(ExistingEntityCard.class);
    ExistingEntityCard deserializedExisting = (ExistingEntityCard) deserialized.actors().get(0);
    assertThat(deserializedExisting.cardId()).isEqualTo(existingCardId);
    assertThat(deserializedExisting.entityId()).isEqualTo(entityId);

    assertThat(deserialized.spaces()).hasSize(1);
    assertThat(deserialized.spaces().get(0)).isInstanceOf(NewEntityCard.class);
    NewEntityCard deserializedNew = (NewEntityCard) deserialized.spaces().get(0);
    assertThat(deserializedNew.cardId()).isEqualTo(newCardId);
    assertThat(deserializedNew.name()).isEqualTo("The Fortress");

    assertThat(deserialized.events()).hasSize(1);
    assertThat(deserialized.events().get(0)).isInstanceOf(UncertainEntityCard.class);
    UncertainEntityCard deserializedUncertain = (UncertainEntityCard) deserialized.events().get(0);
    assertThat(deserializedUncertain.cardId()).isEqualTo(uncertainCardId);
    assertThat(deserializedUncertain.extractedMention()).isEqualTo("The Battle");
    assertThat(deserializedUncertain.candidateEntityId()).isNull();

    assertThat(deserialized.relations()).isEmpty();

    assertThat(deserialized.detectedConflicts()).hasSize(1);
    ConflictCard deserializedConflict = deserialized.detectedConflicts().get(0);
    assertThat(deserializedConflict.conflictId()).isEqualTo(conflictId);
    assertThat(deserializedConflict.entityId()).isEqualTo(conflictEntityId);
    assertThat(deserializedConflict.entityType()).isEqualTo("actor");
    assertThat(deserializedConflict.description()).isEqualTo("Conflict in timeline");
    assertThat(deserializedConflict.extractedFact()).isEqualTo("Mira is dead");
    assertThat(deserializedConflict.existingFact()).isEqualTo("Mira is alive");
  }
}
