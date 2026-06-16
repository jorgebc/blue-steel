package com.bluesteel.application.session;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bluesteel.application.model.commit.AcknowledgedConflict;
import com.bluesteel.application.model.commit.AddedEntity;
import com.bluesteel.application.model.commit.CardAction;
import com.bluesteel.application.model.commit.CardDecision;
import com.bluesteel.application.model.commit.CommitPayload;
import com.bluesteel.application.model.commit.ResolutionType;
import com.bluesteel.application.model.commit.UncertainResolution;
import com.bluesteel.application.model.session.ConflictCard;
import com.bluesteel.application.model.session.DiffPayload;
import com.bluesteel.application.model.session.ExistingEntityCard;
import com.bluesteel.application.model.session.NewEntityCard;
import com.bluesteel.application.model.session.UncertainEntityCard;
import com.bluesteel.application.port.out.worldstate.WorldStatePort;
import com.bluesteel.application.service.session.CommitPayloadValidator;
import com.bluesteel.domain.exception.CommitValidationException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CommitPayloadValidator")
class CommitPayloadValidatorTest {

  private WorldStatePort worldStatePort;
  private CommitPayloadValidator validator;

  private final UUID campaignId = UUID.randomUUID();
  private final UUID existingCardId = UUID.randomUUID();
  private final UUID newCardId = UUID.randomUUID();
  private final UUID uncertainCardId = UUID.randomUUID();
  private final UUID conflictId = UUID.randomUUID();
  private final UUID matchedEntityId = UUID.randomUUID();

  private DiffPayload minimalDiff;

  @BeforeEach
  void setUp() {
    worldStatePort = mock(WorldStatePort.class);
    validator = new CommitPayloadValidator(worldStatePort);

    minimalDiff =
        new DiffPayload(
            "header",
            List.of(
                new ExistingEntityCard(
                    existingCardId, UUID.randomUUID(), "actor", "Frodo", Map.of())),
            List.of(new NewEntityCard(newCardId, "space", "Shire", Map.of())),
            List.of(),
            List.of(),
            List.of(
                new ConflictCard(
                    conflictId, UUID.randomUUID(), "actor", "desc", "fact1", "fact2")));
  }

  private CommitPayload validPayload() {
    return new CommitPayload(
        List.of(
            new CardDecision(existingCardId, CardAction.ACCEPT, null),
            new CardDecision(newCardId, CardAction.ACCEPT, null)),
        List.of(),
        List.of(new AcknowledgedConflict(conflictId)));
  }

  @Test
  @DisplayName("should pass validation when payload is fully valid")
  void validate_validPayload_noException() {
    assertThatNoException()
        .isThrownBy(() -> validator.validate(minimalDiff, validPayload(), campaignId));
  }

  @Test
  @DisplayName("should throw UNKNOWN_CARD_ID when a decision references a card not in the diff")
  void validate_unknownCardId_throwsUnknownCardId() {
    CommitPayload payload =
        new CommitPayload(
            List.of(new CardDecision(UUID.randomUUID(), CardAction.ACCEPT, null)),
            List.of(),
            List.of(new AcknowledgedConflict(conflictId)));

    assertThatThrownBy(() -> validator.validate(minimalDiff, payload, campaignId))
        .isInstanceOf(CommitValidationException.class)
        .extracting("code")
        .isEqualTo("UNKNOWN_CARD_ID");
  }

  @Test
  @DisplayName("should throw DUPLICATE_CARD_DECISION when the same cardId appears twice")
  void validate_duplicateCardId_throwsDuplicateCardDecision() {
    CommitPayload payload =
        new CommitPayload(
            List.of(
                new CardDecision(existingCardId, CardAction.ACCEPT, null),
                new CardDecision(existingCardId, CardAction.DELETE, null),
                new CardDecision(newCardId, CardAction.ACCEPT, null)),
            List.of(),
            List.of(new AcknowledgedConflict(conflictId)));

    assertThatThrownBy(() -> validator.validate(minimalDiff, payload, campaignId))
        .isInstanceOf(CommitValidationException.class)
        .extracting("code")
        .isEqualTo("DUPLICATE_CARD_DECISION");
  }

  @Test
  @DisplayName("should throw INCOMPLETE_CARD_DECISIONS when a non-UNCERTAIN card has no decision")
  void validate_missingDecision_throwsIncompleteCardDecisions() {
    CommitPayload payload =
        new CommitPayload(
            List.of(new CardDecision(existingCardId, CardAction.ACCEPT, null)),
            List.of(),
            List.of(new AcknowledgedConflict(conflictId)));

    assertThatThrownBy(() -> validator.validate(minimalDiff, payload, campaignId))
        .isInstanceOf(CommitValidationException.class)
        .extracting("code")
        .isEqualTo("INCOMPLETE_CARD_DECISIONS");
  }

  @Test
  @DisplayName("should throw UNCERTAIN_ENTITIES_PRESENT when an UNCERTAIN card is not resolved")
  void validate_unresolvedUncertain_throwsUncertainEntitiesPresent() {
    DiffPayload diffWithUncertain =
        new DiffPayload(
            "header",
            List.of(new UncertainEntityCard(uncertainCardId, "actor", "Strider", null)),
            List.of(),
            List.of(),
            List.of(),
            List.of());

    CommitPayload payload = new CommitPayload(List.of(), List.of(), List.of());

    assertThatThrownBy(() -> validator.validate(diffWithUncertain, payload, campaignId))
        .isInstanceOf(CommitValidationException.class)
        .extracting("code")
        .isEqualTo("UNCERTAIN_ENTITIES_PRESENT");
  }

  @Test
  @DisplayName("should throw CONFLICTS_NOT_ACKNOWLEDGED when a conflict is not acknowledged")
  void validate_unacknowledgedConflict_throwsConflictsNotAcknowledged() {
    CommitPayload payload =
        new CommitPayload(
            List.of(
                new CardDecision(existingCardId, CardAction.ACCEPT, null),
                new CardDecision(newCardId, CardAction.ACCEPT, null)),
            List.of(),
            List.of());

    assertThatThrownBy(() -> validator.validate(minimalDiff, payload, campaignId))
        .isInstanceOf(CommitValidationException.class)
        .extracting("code")
        .isEqualTo("CONFLICTS_NOT_ACKNOWLEDGED");
  }

  @Test
  @DisplayName(
      "should throw INVALID_ENTITY_REFERENCE when MATCH resolution refers to cross-campaign entity")
  void validate_matchResolutionCrossCampaign_throwsInvalidEntityReference() {
    DiffPayload diffWithUncertain =
        new DiffPayload(
            "header",
            List.of(new UncertainEntityCard(uncertainCardId, "actor", "Strider", null)),
            List.of(),
            List.of(),
            List.of(),
            List.of());

    when(worldStatePort.existsInCampaign("actor", matchedEntityId, campaignId)).thenReturn(false);

    CommitPayload payload =
        new CommitPayload(
            List.of(),
            List.of(
                new UncertainResolution(uncertainCardId, ResolutionType.MATCH, matchedEntityId)),
            List.of());

    assertThatThrownBy(() -> validator.validate(diffWithUncertain, payload, campaignId))
        .isInstanceOf(CommitValidationException.class)
        .extracting("code")
        .isEqualTo("INVALID_ENTITY_REFERENCE");
  }

  @Test
  @DisplayName("should pass when MATCH resolution refers to an entity in the same campaign")
  void validate_matchResolutionSameCampaign_noException() {
    DiffPayload diffWithUncertain =
        new DiffPayload(
            "header",
            List.of(new UncertainEntityCard(uncertainCardId, "actor", "Strider", null)),
            List.of(),
            List.of(),
            List.of(),
            List.of());

    when(worldStatePort.existsInCampaign("actor", matchedEntityId, campaignId)).thenReturn(true);

    CommitPayload payload =
        new CommitPayload(
            List.of(),
            List.of(
                new UncertainResolution(uncertainCardId, ResolutionType.MATCH, matchedEntityId)),
            List.of());

    assertThatNoException()
        .isThrownBy(() -> validator.validate(diffWithUncertain, payload, campaignId));
  }

  private CommitPayload payloadWithAddedEntities(List<AddedEntity> added) {
    return new CommitPayload(
        List.of(
            new CardDecision(existingCardId, CardAction.ACCEPT, null),
            new CardDecision(newCardId, CardAction.ACCEPT, null)),
        List.of(),
        List.of(new AcknowledgedConflict(conflictId)),
        added);
  }

  @Test
  @DisplayName("should pass validation when an added entity is well-formed")
  void validate_validAddedEntity_noException() {
    CommitPayload payload =
        payloadWithAddedEntities(
            List.of(new AddedEntity("actor", "Gandalf", Map.of("description", "A grey wizard"))));

    assertThatNoException().isThrownBy(() -> validator.validate(minimalDiff, payload, campaignId));
  }

  @Test
  @DisplayName("should throw INVALID_ADDED_ENTITY when an added entity type is unknown")
  void validate_addedEntityUnknownType_throwsInvalidAddedEntity() {
    CommitPayload payload =
        payloadWithAddedEntities(List.of(new AddedEntity("dragon", "Smaug", Map.of())));

    assertThatThrownBy(() -> validator.validate(minimalDiff, payload, campaignId))
        .isInstanceOf(CommitValidationException.class)
        .extracting("code")
        .isEqualTo("INVALID_ADDED_ENTITY");
  }

  @Test
  @DisplayName("should throw INVALID_ADDED_ENTITY when an added entity name is blank")
  void validate_addedEntityBlankName_throwsInvalidAddedEntity() {
    CommitPayload payload =
        payloadWithAddedEntities(List.of(new AddedEntity("actor", "  ", Map.of())));

    assertThatThrownBy(() -> validator.validate(minimalDiff, payload, campaignId))
        .isInstanceOf(CommitValidationException.class)
        .extracting("code")
        .isEqualTo("INVALID_ADDED_ENTITY");
  }

  @Test
  @DisplayName("should throw INVALID_ADDED_ENTITY when an added entity fields map is null")
  void validate_addedEntityNullFields_throwsInvalidAddedEntity() {
    CommitPayload payload =
        payloadWithAddedEntities(List.of(new AddedEntity("space", "Rivendell", null)));

    assertThatThrownBy(() -> validator.validate(minimalDiff, payload, campaignId))
        .isInstanceOf(CommitValidationException.class)
        .extracting("code")
        .isEqualTo("INVALID_ADDED_ENTITY");
  }
}
