package com.bluesteel.application.model.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DiffCard — Jackson polymorphic round-trip")
class DiffCardTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
  }

  @Test
  @DisplayName("should serialize and deserialize ExistingEntityCard with EXISTING discriminator")
  void existingEntityCard_roundTripsThroughJackson() throws Exception {
    UUID cardId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    UUID entityId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    ExistingEntityCard original =
        new ExistingEntityCard(
            cardId, entityId, "actor", "Mira", Map.of("description", "Brave warrior"));

    String json = objectMapper.writeValueAsString(original);
    DiffCard deserialized = objectMapper.readValue(json, DiffCard.class);

    assertThat(deserialized).isInstanceOf(ExistingEntityCard.class);
    ExistingEntityCard result = (ExistingEntityCard) deserialized;
    assertThat(result.cardId()).isEqualTo(cardId);
    assertThat(result.entityId()).isEqualTo(entityId);
    assertThat(result.entityType()).isEqualTo("actor");
    assertThat(result.name()).isEqualTo("Mira");
    assertThat(result.changedFields()).containsEntry("description", "Brave warrior");
    assertThat(json).contains("\"cardType\":\"EXISTING\"");
  }

  @Test
  @DisplayName("should serialize and deserialize NewEntityCard with NEW discriminator")
  void newEntityCard_roundTripsThroughJackson() throws Exception {
    UUID cardId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    NewEntityCard original =
        new NewEntityCard(
            cardId,
            "space",
            "The Dark Fortress",
            Map.of("name", "The Dark Fortress", "description", "A forbidding stronghold"));

    String json = objectMapper.writeValueAsString(original);
    DiffCard deserialized = objectMapper.readValue(json, DiffCard.class);

    assertThat(deserialized).isInstanceOf(NewEntityCard.class);
    NewEntityCard result = (NewEntityCard) deserialized;
    assertThat(result.cardId()).isEqualTo(cardId);
    assertThat(result.entityType()).isEqualTo("space");
    assertThat(result.name()).isEqualTo("The Dark Fortress");
    assertThat(result.fullProfile()).containsEntry("description", "A forbidding stronghold");
    assertThat(json).contains("\"cardType\":\"NEW\"");
  }

  @Test
  @DisplayName(
      "should serialize and deserialize UncertainEntityCard with UNCERTAIN discriminator and null candidateEntityId")
  void uncertainEntityCard_roundTripsThroughJackson() throws Exception {
    UUID cardId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    UncertainEntityCard original = new UncertainEntityCard(cardId, "actor", "The Shadow", null);

    String json = objectMapper.writeValueAsString(original);
    DiffCard deserialized = objectMapper.readValue(json, DiffCard.class);

    assertThat(deserialized).isInstanceOf(UncertainEntityCard.class);
    UncertainEntityCard result = (UncertainEntityCard) deserialized;
    assertThat(result.cardId()).isEqualTo(cardId);
    assertThat(result.entityType()).isEqualTo("actor");
    assertThat(result.extractedMention()).isEqualTo("The Shadow");
    assertThat(result.candidateEntityId()).isNull();
    assertThat(json).contains("\"cardType\":\"UNCERTAIN\"");
  }
}
