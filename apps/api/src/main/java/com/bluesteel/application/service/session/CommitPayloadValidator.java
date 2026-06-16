package com.bluesteel.application.service.session;

import com.bluesteel.application.model.commit.AddedEntity;
import com.bluesteel.application.model.commit.CardDecision;
import com.bluesteel.application.model.commit.CommitPayload;
import com.bluesteel.application.model.commit.ResolutionType;
import com.bluesteel.application.model.commit.UncertainResolution;
import com.bluesteel.application.model.session.DiffCard;
import com.bluesteel.application.model.session.DiffPayload;
import com.bluesteel.application.model.session.UncertainEntityCard;
import com.bluesteel.application.port.out.worldstate.WorldStatePort;
import com.bluesteel.domain.exception.CommitValidationException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Application-layer gatekeeper for commit payload integrity (D-081). All checks run before any
 * world-state write. A single failing check throws {@link CommitValidationException} immediately.
 */
@Component
public class CommitPayloadValidator {

  /**
   * Entity types a reviewer may add manually (F6.1, D-053). Limited to the self-contained concepts:
   * events and relations depend on structured links (endpoints, involved actors, event type) the
   * add-entity form cannot supply, so they are rejected here as defence in depth.
   */
  private static final Set<String> ADDABLE_ENTITY_TYPES = Set.of("actor", "space");

  private final WorldStatePort worldStatePort;

  public CommitPayloadValidator(WorldStatePort worldStatePort) {
    this.worldStatePort = worldStatePort;
  }

  /**
   * Validates {@code payload} against the stored {@code storedDiff} for the given campaign.
   *
   * @throws CommitValidationException with a machine-readable code on the first failing check
   */
  public void validate(DiffPayload storedDiff, CommitPayload payload, UUID campaignId) {
    Map<UUID, DiffCard> allCards = buildCardMap(storedDiff);
    List<CardDecision> decisions = payload.cardDecisions();

    // Check 1: all cardIds in decisions must exist in the stored diff
    for (CardDecision decision : decisions) {
      if (!allCards.containsKey(decision.cardId())) {
        throw new CommitValidationException(
            "UNKNOWN_CARD_ID", "Card id not found in diff: " + decision.cardId());
      }
    }

    // Check 2: no duplicate cardIds in decisions
    Set<UUID> seen = new HashSet<>();
    for (CardDecision decision : decisions) {
      if (!seen.add(decision.cardId())) {
        throw new CommitValidationException(
            "DUPLICATE_CARD_DECISION", "Duplicate card decision for card: " + decision.cardId());
      }
    }

    Set<UUID> decidedCardIds =
        decisions.stream().map(CardDecision::cardId).collect(Collectors.toSet());

    // Check 3: every non-UNCERTAIN card must have a decision
    for (DiffCard card : allCards.values()) {
      if (!(card instanceof UncertainEntityCard) && !decidedCardIds.contains(cardId(card))) {
        throw new CommitValidationException(
            "INCOMPLETE_CARD_DECISIONS", "No decision provided for card: " + cardId(card));
      }
    }

    // Check 4: every UNCERTAIN card must have a resolution
    Set<UUID> resolvedCardIds =
        payload.uncertainResolutions().stream()
            .map(UncertainResolution::cardId)
            .collect(Collectors.toSet());
    for (DiffCard card : allCards.values()) {
      if (card instanceof UncertainEntityCard && !resolvedCardIds.contains(cardId(card))) {
        throw new CommitValidationException(
            "UNCERTAIN_ENTITIES_PRESENT",
            "Uncertain entity not resolved for card: " + cardId(card));
      }
    }

    // Check 5: every conflict must be acknowledged
    Set<UUID> acknowledgedIds =
        payload.acknowledgedConflicts().stream()
            .map(a -> a.conflictId())
            .collect(Collectors.toSet());
    for (var conflict : storedDiff.detectedConflicts()) {
      if (!acknowledgedIds.contains(conflict.conflictId())) {
        throw new CommitValidationException(
            "CONFLICTS_NOT_ACKNOWLEDGED", "Conflict not acknowledged: " + conflict.conflictId());
      }
    }

    // Check 6: no unsupported actions (defensive — CardAction enum excludes ADD, D-053)
    for (CardDecision decision : decisions) {
      switch (decision.action()) {
        case ACCEPT, EDIT, DELETE -> {
          /* supported */
        }
        default ->
            throw new CommitValidationException(
                "UNSUPPORTED_ACTION", "Action not supported: " + decision.action());
      }
    }

    // Check 7: MATCH resolutions must refer to an entity that exists in this campaign (D-079)
    for (UncertainResolution resolution : payload.uncertainResolutions()) {
      if (resolution.resolution() == ResolutionType.MATCH) {
        UncertainEntityCard card = (UncertainEntityCard) allCards.get(resolution.cardId());
        if (!worldStatePort.existsInCampaign(
            card.entityType(), resolution.matchedEntityId(), campaignId)) {
          throw new CommitValidationException(
              "INVALID_ENTITY_REFERENCE",
              "Matched entity does not exist in campaign: " + resolution.matchedEntityId());
        }
      }
    }

    // Check 8: each reviewer-added entity must declare an addable type, a non-blank name, and a
    // non-null fields map (F6.1, D-053). This replaces the former defensive `add`-action rejection
    // (UNSUPPORTED_ACTION) with positive validation of the dedicated addedEntities list.
    for (AddedEntity added : payload.addedEntities()) {
      if (added.entityType() == null || !ADDABLE_ENTITY_TYPES.contains(added.entityType())) {
        throw new CommitValidationException(
            "INVALID_ADDED_ENTITY", "Entity type cannot be added manually: " + added.entityType());
      }
      if (added.name() == null || added.name().isBlank()) {
        throw new CommitValidationException(
            "INVALID_ADDED_ENTITY", "Added entity name must not be blank");
      }
      if (added.fields() == null) {
        throw new CommitValidationException(
            "INVALID_ADDED_ENTITY", "Added entity fields must not be null: " + added.name());
      }
      // Check 9: manual-add bypasses entity resolution, so guard world-state integrity by rejecting
      // a name that collides (case-insensitive) with a same-type new card in this diff or an
      // already-committed entity of the same type (F6.1, D-053).
      String trimmedName = added.name().trim();
      if (collidesWithNewCard(allCards, added.entityType(), trimmedName)
          || worldStatePort
              .findEntityIdByName(campaignId, trimmedName, added.entityType())
              .isPresent()) {
        throw new CommitValidationException(
            "ADDED_ENTITY_NAME_COLLISION",
            "An entity with this name already exists: " + trimmedName);
      }
    }
  }

  private static boolean collidesWithNewCard(
      Map<UUID, DiffCard> allCards, String entityType, String name) {
    return allCards.values().stream()
        .anyMatch(
            card ->
                card instanceof com.bluesteel.application.model.session.NewEntityCard newCard
                    && entityType.equals(newCard.entityType())
                    && newCard.name().equalsIgnoreCase(name));
  }

  private static Map<UUID, DiffCard> buildCardMap(DiffPayload diff) {
    return Stream.of(diff.actors(), diff.spaces(), diff.events(), diff.relations())
        .flatMap(List::stream)
        .collect(Collectors.toMap(CommitPayloadValidator::cardId, c -> c));
  }

  private static UUID cardId(DiffCard card) {
    return switch (card) {
      case com.bluesteel.application.model.session.ExistingEntityCard c -> c.cardId();
      case com.bluesteel.application.model.session.NewEntityCard c -> c.cardId();
      case UncertainEntityCard c -> c.cardId();
    };
  }
}
