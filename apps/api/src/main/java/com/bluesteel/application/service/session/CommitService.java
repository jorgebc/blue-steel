package com.bluesteel.application.service.session;

import com.bluesteel.application.event.SessionCommittedEvent;
import com.bluesteel.application.model.commit.AddedEntity;
import com.bluesteel.application.model.commit.CardDecision;
import com.bluesteel.application.model.commit.ResolutionType;
import com.bluesteel.application.model.commit.UncertainResolution;
import com.bluesteel.application.model.session.CommitSessionCommand;
import com.bluesteel.application.model.session.DiffCard;
import com.bluesteel.application.model.session.DiffPayload;
import com.bluesteel.application.model.session.ExistingEntityCard;
import com.bluesteel.application.model.session.NewEntityCard;
import com.bluesteel.application.model.session.UncertainEntityCard;
import com.bluesteel.application.model.worldstate.CommittedEntityVersion;
import com.bluesteel.application.model.worldstate.EntityWriteCommand;
import com.bluesteel.application.model.worldstate.ResolvedEndpoint;
import com.bluesteel.application.port.in.session.CommitSessionUseCase;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.session.SessionRepository;
import com.bluesteel.application.port.out.worldstate.WorldStatePort;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.exception.InvalidSessionStateTransitionException;
import com.bluesteel.domain.exception.SessionNotFoundException;
import com.bluesteel.domain.exception.UnauthorizedException;
import com.bluesteel.domain.session.Session;
import com.bluesteel.domain.session.SessionStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Orchestrates the commit: validates payload, writes world state, transitions session (D-081). */
@Service
public class CommitService implements CommitSessionUseCase {

  private static final Logger log = LoggerFactory.getLogger(CommitService.class);

  private final CampaignMembershipPort membershipPort;
  private final SessionRepository sessionRepository;
  private final WorldStatePort worldStatePort;
  private final CommitPayloadValidator validator;
  private final ObjectMapper objectMapper;
  private final ApplicationEventPublisher eventPublisher;

  public CommitService(
      CampaignMembershipPort membershipPort,
      SessionRepository sessionRepository,
      WorldStatePort worldStatePort,
      CommitPayloadValidator validator,
      ObjectMapper objectMapper,
      ApplicationEventPublisher eventPublisher) {
    this.membershipPort = membershipPort;
    this.sessionRepository = sessionRepository;
    this.worldStatePort = worldStatePort;
    this.validator = validator;
    this.objectMapper = objectMapper;
    this.eventPublisher = eventPublisher;
  }

  @Override
  @Transactional
  public void commit(CommitSessionCommand command) {
    log.info(
        "Committing session sessionId={} callerId={} campaignId={}",
        command.sessionId(),
        command.callerId(),
        command.campaignId());

    CampaignRole role =
        membershipPort
            .resolveRole(command.campaignId(), command.callerId())
            .orElseThrow(
                () -> new UnauthorizedException("Caller is not a member of this campaign"));

    if (role != CampaignRole.GM && role != CampaignRole.EDITOR) {
      throw new UnauthorizedException("Only GMs and Editors may commit sessions");
    }

    Session session =
        sessionRepository
            .findById(command.sessionId())
            .filter(s -> s.campaignId().equals(command.campaignId()))
            .orElseThrow(
                () -> new SessionNotFoundException("Session not found: " + command.sessionId()));

    if (session.status() != SessionStatus.DRAFT) {
      throw new InvalidSessionStateTransitionException(
          "commit requires DRAFT but was " + session.status());
    }

    DiffPayload storedDiff = deserializeDiff(session.diffPayload());

    // Validate before any world-state write (D-081)
    validator.validate(storedDiff, command.payload(), command.campaignId());

    List<CommittedEntityVersion> versions = new ArrayList<>();
    Map<UUID, DiffCard> cardMap = buildCardMap(storedDiff);

    // Write order matters because event and relation cards name-match their links against the
    // actor/space heads committed in this same session (F4.3.4, F4.6.4, D-095). So: plain
    // actor/space cards first, then UNCERTAIN resolutions (also actors/spaces), then events
    // (resolve space/involved-actor mentions), then relations (resolve source/target) — regardless
    // of the order the client listed the decisions in.
    for (CardDecision decision : command.payload().cardDecisions()) {
      DiffCard card = cardMap.get(decision.cardId());
      if (!isEventCard(card) && !isRelationCard(card)) {
        writeDecision(decision, cardMap, session, versions);
      }
    }

    for (UncertainResolution resolution : command.payload().uncertainResolutions()) {
      UncertainEntityCard uncertainCard = (UncertainEntityCard) cardMap.get(resolution.cardId());
      EntityWriteCommand cmd = buildUncertainWriteCommand(uncertainCard, resolution, session);
      versions.add(worldStatePort.writeEntity(cmd));
    }

    // Reviewer-added entities (F6.1, D-053) ride the same type-ordered write so added actors/spaces
    // are committed before added events/relations can name-match their links against them.
    List<AddedEntity> added = command.payload().addedEntities();
    writeAddedEntities(added, t -> !"event".equals(t) && !"relation".equals(t), session, versions);

    for (CardDecision decision : command.payload().cardDecisions()) {
      if (isEventCard(cardMap.get(decision.cardId()))) {
        writeDecision(decision, cardMap, session, versions);
      }
    }
    writeAddedEntities(added, "event"::equals, session, versions);

    for (CardDecision decision : command.payload().cardDecisions()) {
      if (isRelationCard(cardMap.get(decision.cardId()))) {
        writeDecision(decision, cardMap, session, versions);
      }
    }
    writeAddedEntities(added, "relation"::equals, session, versions);

    int seq = sessionRepository.nextSequenceNumber(command.campaignId());
    session.commit(seq);
    sessionRepository.save(session);

    eventPublisher.publishEvent(
        new SessionCommittedEvent(command.sessionId(), command.campaignId(), versions));

    log.info("Session committed sessionId={} sequenceNumber={}", command.sessionId(), seq);
  }

  private DiffPayload deserializeDiff(String json) {
    try {
      return objectMapper.readValue(json, DiffPayload.class);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to deserialize stored diff payload", e);
    }
  }

  private static Map<UUID, DiffCard> buildCardMap(DiffPayload diff) {
    Map<UUID, DiffCard> map = new HashMap<>();
    Stream.of(diff.actors(), diff.spaces(), diff.events(), diff.relations())
        .flatMap(List::stream)
        .forEach(c -> map.put(cardId(c), c));
    return map;
  }

  private static UUID cardId(DiffCard card) {
    return switch (card) {
      case ExistingEntityCard c -> c.cardId();
      case NewEntityCard c -> c.cardId();
      case UncertainEntityCard c -> c.cardId();
    };
  }

  private void writeDecision(
      CardDecision decision,
      Map<UUID, DiffCard> cardMap,
      Session session,
      List<CommittedEntityVersion> versions) {
    DiffCard card = cardMap.get(decision.cardId());
    switch (decision.action()) {
      case ACCEPT, EDIT ->
          versions.add(worldStatePort.writeEntity(buildWriteCommand(card, decision, session)));
      case DELETE -> {
        /* skip — entity is intentionally omitted */
      }
    }
  }

  private static boolean isRelationCard(DiffCard card) {
    return "relation".equals(entityTypeOf(card));
  }

  private static boolean isEventCard(DiffCard card) {
    return "event".equals(entityTypeOf(card));
  }

  private static String entityTypeOf(DiffCard card) {
    return switch (card) {
      case ExistingEntityCard c -> c.entityType();
      case NewEntityCard c -> c.entityType();
      case UncertainEntityCard c -> c.entityType();
    };
  }

  private void writeAddedEntities(
      List<AddedEntity> added,
      Predicate<String> typeFilter,
      Session session,
      List<CommittedEntityVersion> versions) {
    for (AddedEntity entity : added) {
      if (typeFilter.test(entity.entityType())) {
        versions.add(worldStatePort.writeEntity(buildAddedEntityWriteCommand(entity, session)));
      }
    }
  }

  /**
   * Builds the write command for a reviewer-added entity: a brand-new head ({@code existingEntityId
   * == null}) whose full snapshot is the supplied fields, stamped with the committing session.
   * Mirrors the {@link NewEntityCard} branch, including link resolution so added events/relations
   * resolve their mentions (F6.1, D-053).
   */
  private EntityWriteCommand buildAddedEntityWriteCommand(AddedEntity entity, Session session) {
    Map<String, Object> fullSnapshot = new HashMap<>(entity.fields());
    EntityWriteCommand cmd =
        new EntityWriteCommand(
            entity.entityType(),
            null,
            session.campaignId(),
            session.ownerId(),
            entity.name(),
            null,
            fullSnapshot,
            session.id());
    return applyLinks(cmd, fullSnapshot, session.campaignId());
  }

  private EntityWriteCommand buildWriteCommand(
      DiffCard card, CardDecision decision, Session session) {
    return switch (card) {
      case ExistingEntityCard c -> {
        Map<String, Object> changedFields = new HashMap<>(c.changedFields());
        if (decision.editedFields() != null) {
          changedFields.putAll(decision.editedFields());
        }
        EntityWriteCommand cmd =
            new EntityWriteCommand(
                c.entityType(),
                c.entityId(),
                session.campaignId(),
                session.ownerId(),
                c.name(),
                changedFields,
                changedFields,
                session.id());
        yield applyLinks(cmd, changedFields, session.campaignId());
      }
      case NewEntityCard c -> {
        Map<String, Object> fullSnapshot = new HashMap<>(c.fullProfile());
        if (decision.editedFields() != null) {
          fullSnapshot.putAll(decision.editedFields());
        }
        EntityWriteCommand cmd =
            new EntityWriteCommand(
                c.entityType(),
                null,
                session.campaignId(),
                session.ownerId(),
                c.name(),
                null,
                fullSnapshot,
                session.id());
        yield applyLinks(cmd, fullSnapshot, session.campaignId());
      }
      case UncertainEntityCard c ->
          throw new IllegalStateException(
              "UNCERTAIN card should be handled via uncertainResolutions, not cardDecisions");
    };
  }

  /**
   * Resolves the structured links stashed in a card snapshot, dispatching by entity type: relation
   * source/target endpoints (F4.3.4) and event space/involved-actor/type links (F4.6.4). A command
   * whose type matches neither is returned unchanged.
   */
  private EntityWriteCommand applyLinks(
      EntityWriteCommand cmd, Map<String, Object> snapshot, UUID campaignId) {
    return applyEventLinks(applyRelationEndpoints(cmd, snapshot, campaignId), snapshot, campaignId);
  }

  /**
   * For relation cards, best-effort resolves the {@code sourceMention}/{@code targetMention} names
   * stashed in the card snapshot to committed actor/space ids and sets them on the command;
   * unresolved endpoints stay null (F4.3.4, D-095). Non-relation commands are returned unchanged.
   */
  private EntityWriteCommand applyRelationEndpoints(
      EntityWriteCommand cmd, Map<String, Object> snapshot, UUID campaignId) {
    if (!"relation".equals(cmd.entityType())) {
      return cmd;
    }
    ResolvedEndpoint source = resolveEndpoint(snapshot.get("sourceMention"), campaignId);
    ResolvedEndpoint target = resolveEndpoint(snapshot.get("targetMention"), campaignId);
    return cmd.withEndpoints(
        source == null ? null : source.entityId(),
        source == null ? null : source.entityType(),
        target == null ? null : target.entityId(),
        target == null ? null : target.entityType());
  }

  private ResolvedEndpoint resolveEndpoint(Object mentionName, UUID campaignId) {
    if (!(mentionName instanceof String name) || name.isBlank()) {
      return null;
    }
    return worldStatePort.findEndpointByName(campaignId, name).orElse(null);
  }

  /**
   * For event cards, best-effort resolves the {@code spaceMention} to a committed space id and each
   * {@code involvedActorMentions} entry to a committed actor id, and carries the {@code eventType}
   * through; unresolved mentions are dropped (F4.6.4, D-095, D-097). Non-event commands are
   * returned unchanged.
   */
  private EntityWriteCommand applyEventLinks(
      EntityWriteCommand cmd, Map<String, Object> snapshot, UUID campaignId) {
    if (!"event".equals(cmd.entityType())) {
      return cmd;
    }
    UUID spaceId = resolveLink(snapshot.get("spaceMention"), campaignId, "space");
    List<UUID> actorIds = resolveActorLinks(snapshot.get("involvedActorMentions"), campaignId);
    String eventType =
        snapshot.get("eventType") instanceof String type && !type.isBlank() ? type : null;
    return cmd.withEventLinks(spaceId, actorIds, eventType);
  }

  private UUID resolveLink(Object mentionName, UUID campaignId, String entityType) {
    if (!(mentionName instanceof String name) || name.isBlank()) {
      return null;
    }
    return worldStatePort.findEntityIdByName(campaignId, name, entityType).orElse(null);
  }

  private List<UUID> resolveActorLinks(Object mentions, UUID campaignId) {
    if (!(mentions instanceof List<?> list)) {
      return List.of();
    }
    List<UUID> ids = new ArrayList<>();
    for (Object mention : list) {
      UUID id = resolveLink(mention, campaignId, "actor");
      if (id != null && !ids.contains(id)) {
        ids.add(id);
      }
    }
    return ids;
  }

  private EntityWriteCommand buildUncertainWriteCommand(
      UncertainEntityCard card, UncertainResolution resolution, Session session) {
    UUID existingEntityId =
        resolution.resolution() == ResolutionType.MATCH ? resolution.matchedEntityId() : null;
    return new EntityWriteCommand(
        card.entityType(),
        existingEntityId,
        session.campaignId(),
        session.ownerId(),
        card.extractedMention(),
        null,
        Map.of("name", card.extractedMention()),
        session.id());
  }
}
