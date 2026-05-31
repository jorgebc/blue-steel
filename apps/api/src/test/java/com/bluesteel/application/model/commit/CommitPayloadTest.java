package com.bluesteel.application.model.commit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CommitPayload Jackson round-trip")
class CommitPayloadTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("should round-trip CommitPayload with camelCase keys and lowercase CardAction")
  void roundTrip_camelCaseAndLowercaseAction() throws Exception {
    UUID cardId = UUID.randomUUID();
    UUID conflictId = UUID.randomUUID();
    UUID uncertainCardId = UUID.randomUUID();
    UUID matchedEntityId = UUID.randomUUID();

    CommitPayload payload =
        new CommitPayload(
            List.of(
                new CardDecision(cardId, CardAction.ACCEPT, null),
                new CardDecision(UUID.randomUUID(), CardAction.EDIT, Map.of("name", "Updated")),
                new CardDecision(UUID.randomUUID(), CardAction.DELETE, null)),
            List.of(
                new UncertainResolution(uncertainCardId, ResolutionType.MATCH, matchedEntityId),
                new UncertainResolution(UUID.randomUUID(), ResolutionType.NEW, null)),
            List.of(new AcknowledgedConflict(conflictId)));

    String json = objectMapper.writeValueAsString(payload);

    assertThat(json).contains("\"cardDecisions\"");
    assertThat(json).contains("\"uncertainResolutions\"");
    assertThat(json).contains("\"acknowledgedConflicts\"");
    assertThat(json).contains("\"action\":\"accept\"");
    assertThat(json).contains("\"action\":\"edit\"");
    assertThat(json).contains("\"action\":\"delete\"");
    assertThat(json).contains("\"resolution\":\"MATCH\"");
    assertThat(json).contains("\"resolution\":\"NEW\"");

    CommitPayload deserialized = objectMapper.readValue(json, CommitPayload.class);

    assertThat(deserialized.cardDecisions()).hasSize(3);
    assertThat(deserialized.cardDecisions().get(0).cardId()).isEqualTo(cardId);
    assertThat(deserialized.cardDecisions().get(0).action()).isEqualTo(CardAction.ACCEPT);
    assertThat(deserialized.cardDecisions().get(1).action()).isEqualTo(CardAction.EDIT);
    assertThat(deserialized.cardDecisions().get(2).action()).isEqualTo(CardAction.DELETE);

    assertThat(deserialized.uncertainResolutions()).hasSize(2);
    assertThat(deserialized.uncertainResolutions().get(0).cardId()).isEqualTo(uncertainCardId);
    assertThat(deserialized.uncertainResolutions().get(0).resolution())
        .isEqualTo(ResolutionType.MATCH);
    assertThat(deserialized.uncertainResolutions().get(0).matchedEntityId())
        .isEqualTo(matchedEntityId);
    assertThat(deserialized.uncertainResolutions().get(1).resolution())
        .isEqualTo(ResolutionType.NEW);
    assertThat(deserialized.uncertainResolutions().get(1).matchedEntityId()).isNull();

    assertThat(deserialized.acknowledgedConflicts()).hasSize(1);
    assertThat(deserialized.acknowledgedConflicts().get(0).conflictId()).isEqualTo(conflictId);
  }
}
