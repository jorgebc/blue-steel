package com.bluesteel.application.service.session;

import com.bluesteel.application.model.ingestion.ConflictWarning;
import com.bluesteel.application.model.ingestion.ExtractedMention;
import com.bluesteel.application.model.ingestion.ExtractedRelation;
import com.bluesteel.application.model.ingestion.ExtractionResult;
import com.bluesteel.application.model.ingestion.ResolutionOutcome;
import com.bluesteel.application.model.ingestion.ResolvedEntity;
import com.bluesteel.application.model.session.ConflictCard;
import com.bluesteel.application.model.session.DiffCard;
import com.bluesteel.application.model.session.DiffPayload;
import com.bluesteel.application.model.session.ExistingEntityCard;
import com.bluesteel.application.model.session.NewEntityCard;
import com.bluesteel.application.model.session.UncertainEntityCard;
import com.bluesteel.application.port.out.session.SessionRepository;
import com.bluesteel.domain.session.Session;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Final pipeline stage: assembles a {@link DiffPayload} from extraction and resolution outputs,
 * serializes it to JSON, and transitions the session to {@code DRAFT} (F2.7.3).
 */
@Service
public class DiffGenerationService {

  private static final Logger log = LoggerFactory.getLogger(DiffGenerationService.class);

  private final SessionRepository sessionRepository;
  private final ObjectMapper objectMapper;

  public DiffGenerationService(SessionRepository sessionRepository, ObjectMapper objectMapper) {
    this.sessionRepository = sessionRepository;
    this.objectMapper = objectMapper;
  }

  /**
   * Builds the diff payload from pipeline outputs, persists it, and transitions the session to
   * DRAFT. On any failure the session is marked FAILED and the exception is rethrown.
   */
  public void run(
      Session session,
      ExtractionResult extraction,
      List<ResolvedEntity> resolved,
      List<ConflictWarning> conflicts) {
    log.info("Starting diff generation stage sessionId={}", session.id());
    try {
      Map<ExtractedMention, ResolvedEntity> byMention =
          resolved.stream()
              .filter(r -> r.mention() != null)
              .collect(Collectors.toMap(ResolvedEntity::mention, Function.identity()));

      DiffPayload payload =
          new DiffPayload(
              extraction.narrativeSummaryHeader(),
              buildCards(extraction.actors(), "actor", byMention),
              buildCards(extraction.spaces(), "space", byMention),
              buildCards(extraction.events(), "event", byMention),
              buildRelationCards(extraction.relations(), byMention),
              buildConflictCards(conflicts, extraction, resolved));

      String json = objectMapper.writeValueAsString(payload);
      session.toDraft(json);
      sessionRepository.save(session);
    } catch (Exception e) {
      session.markFailed("DIFF_GENERATION_FAILED");
      sessionRepository.save(session);
      throw new RuntimeException("Diff generation failed for session " + session.id(), e);
    }
    log.info("Diff generation complete sessionId={}", session.id());
  }

  private List<DiffCard> buildCards(
      List<ExtractedMention> mentions,
      String entityType,
      Map<ExtractedMention, ResolvedEntity> byMention) {
    List<DiffCard> cards = new ArrayList<>(mentions.size());
    for (ExtractedMention mention : mentions) {
      ResolvedEntity resolved = byMention.get(mention);
      if (resolved == null) {
        continue;
      }
      UUID cardId = UUID.randomUUID();
      DiffCard card =
          switch (resolved.outcome()) {
            case MATCH ->
                new ExistingEntityCard(
                    cardId,
                    resolved.matchedEntityId(),
                    entityType,
                    mention.name(),
                    Map.of("description", mention.description()));
            case NEW ->
                new NewEntityCard(
                    cardId,
                    entityType,
                    mention.name(),
                    Map.of("name", mention.name(), "description", mention.description()));
            case UNCERTAIN -> new UncertainEntityCard(cardId, entityType, mention.name(), null);
          };
      cards.add(card);
    }
    return cards;
  }

  /**
   * Builds relation diff cards, mirroring {@link #buildCards} but stashing the relation's
   * source/target endpoint names into the card snapshot so they survive serialize → commit, where
   * they are resolved to entity ids (F4.3.4, D-095).
   */
  private List<DiffCard> buildRelationCards(
      List<ExtractedRelation> relations, Map<ExtractedMention, ResolvedEntity> byMention) {
    List<DiffCard> cards = new ArrayList<>(relations.size());
    for (ExtractedRelation relation : relations) {
      ResolvedEntity resolved = byMention.get(relation.toMention());
      if (resolved == null) {
        continue;
      }
      UUID cardId = UUID.randomUUID();
      DiffCard card =
          switch (resolved.outcome()) {
            case MATCH ->
                new ExistingEntityCard(
                    cardId,
                    resolved.matchedEntityId(),
                    "relation",
                    relation.name(),
                    withEndpoints(Map.of("description", relation.description()), relation));
            case NEW ->
                new NewEntityCard(
                    cardId,
                    "relation",
                    relation.name(),
                    withEndpoints(
                        Map.of("name", relation.name(), "description", relation.description()),
                        relation));
            case UNCERTAIN -> new UncertainEntityCard(cardId, "relation", relation.name(), null);
          };
      cards.add(card);
    }
    return cards;
  }

  /** Returns a copy of {@code base} with non-null relation endpoint mentions added (F4.3.4). */
  private static Map<String, Object> withEndpoints(
      Map<String, Object> base, ExtractedRelation relation) {
    Map<String, Object> snapshot = new LinkedHashMap<>(base);
    if (relation.sourceMention() != null) {
      snapshot.put("sourceMention", relation.sourceMention());
    }
    if (relation.targetMention() != null) {
      snapshot.put("targetMention", relation.targetMention());
    }
    return snapshot;
  }

  private List<ConflictCard> buildConflictCards(
      List<ConflictWarning> conflicts, ExtractionResult extraction, List<ResolvedEntity> resolved) {
    List<ConflictCard> cards = new ArrayList<>(conflicts.size());
    for (ConflictWarning warning : conflicts) {
      UUID entityId = findMatchedEntityId(warning.entityName(), resolved);
      String entityType = findEntityType(warning.entityName(), extraction);
      cards.add(
          new ConflictCard(
              UUID.randomUUID(),
              entityId,
              entityType,
              warning.description(),
              warning.description(),
              warning.description()));
    }
    return cards;
  }

  private UUID findMatchedEntityId(String entityName, List<ResolvedEntity> resolved) {
    return resolved.stream()
        .filter(
            r ->
                r.outcome() == ResolutionOutcome.MATCH
                    && r.mention() != null
                    && r.mention().name().equals(entityName))
        .findFirst()
        .map(ResolvedEntity::matchedEntityId)
        .orElse(null);
  }

  private String findEntityType(String entityName, ExtractionResult extraction) {
    if (containsName(extraction.actors(), entityName)) return "actor";
    if (containsName(extraction.spaces(), entityName)) return "space";
    if (containsName(extraction.events(), entityName)) return "event";
    if (extraction.relations().stream().anyMatch(r -> r.name().equals(entityName)))
      return "relation";
    return null;
  }

  private boolean containsName(List<ExtractedMention> mentions, String name) {
    return mentions.stream().anyMatch(m -> m.name().equals(name));
  }
}
