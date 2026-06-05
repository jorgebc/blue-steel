package com.bluesteel.adapters.out.persistence.worldstate;

import com.bluesteel.application.model.worldstate.CommittedEntityVersion;
import com.bluesteel.application.model.worldstate.EntityWriteCommand;
import com.bluesteel.application.model.worldstate.ResolvedEndpoint;
import com.bluesteel.application.port.out.worldstate.WorldStatePort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Native-SQL implementation of {@link WorldStatePort} over the four head/version table pairs
 * (D-089). Uses {@code JdbcTemplate} rather than Spring Data JPA because world-state writes involve
 * JSONB casts ({@code ?::jsonb}) and per-entity-type table routing that JPA cannot express without
 * dynamic native queries — matching the pattern established by {@link
 * com.bluesteel.adapters.out.persistence.embedding.EntitySimilaritySearchAdapter} (D-062). Entity
 * type is validated against a closed whitelist before any SQL is interpolated.
 */
@Component
public class WorldStateAdapter implements WorldStatePort {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public WorldStateAdapter(@Lazy JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  private record TablePair(String headTable, String versionTable, String fkColumn) {}

  private static TablePair resolveTablePair(String entityType) {
    return switch (entityType) {
      case "actor" -> new TablePair("actors", "actor_versions", "actor_id");
      case "space" -> new TablePair("spaces", "space_versions", "space_id");
      case "event" -> new TablePair("events", "event_versions", "event_id");
      case "relation" -> new TablePair("relations", "relation_versions", "relation_id");
      default -> throw new IllegalArgumentException("Unknown entity type: " + entityType);
    };
  }

  @Override
  public CommittedEntityVersion writeEntity(EntityWriteCommand cmd) {
    TablePair tables = resolveTablePair(cmd.entityType());
    String changedFieldsJson = toJson(cmd.changedFields());
    String fullSnapshotJson = toJson(cmd.fullSnapshot());
    String contentToEmbed = cmd.name() + "\n" + fullSnapshotJson;
    String contentHash = sha256Hex(contentToEmbed);

    UUID entityId;
    UUID entityVersionId = UUID.randomUUID();
    int versionNumber;

    if (cmd.existingEntityId() == null) {
      entityId = UUID.randomUUID();
      versionNumber = 1;

      if ("relation".equals(cmd.entityType())) {
        jdbcTemplate.update(
            "INSERT INTO relations (id, campaign_id, owner_id, name, created_at,"
                + " created_in_session_id, source_entity_id, source_entity_type,"
                + " target_entity_id, target_entity_type)"
                + " VALUES (?, ?, ?, ?, now(), ?, ?, ?, ?, ?)",
            entityId,
            cmd.campaignId(),
            cmd.ownerId(),
            cmd.name(),
            cmd.sessionId(),
            cmd.sourceEntityId(),
            cmd.sourceEntityType(),
            cmd.targetEntityId(),
            cmd.targetEntityType());
      } else if ("event".equals(cmd.entityType())) {
        jdbcTemplate.update(
            "INSERT INTO events (id, campaign_id, owner_id, name, created_at,"
                + " created_in_session_id, space_id, event_type)"
                + " VALUES (?, ?, ?, ?, now(), ?, ?, ?)",
            entityId,
            cmd.campaignId(),
            cmd.ownerId(),
            cmd.name(),
            cmd.sessionId(),
            cmd.spaceId(),
            cmd.eventType());
      } else {
        jdbcTemplate.update(
            "INSERT INTO "
                + tables.headTable()
                + " (id, campaign_id, owner_id, name, created_at, created_in_session_id)"
                + " VALUES (?, ?, ?, ?, now(), ?)",
            entityId,
            cmd.campaignId(),
            cmd.ownerId(),
            cmd.name(),
            cmd.sessionId());
      }
    } else {
      entityId = cmd.existingEntityId();
      versionNumber =
          jdbcTemplate.queryForObject(
              "SELECT COALESCE(MAX(version_number), 0) + 1 FROM "
                  + tables.versionTable()
                  + " WHERE "
                  + tables.fkColumn()
                  + " = ?",
              Integer.class,
              entityId);
      // Refresh the relation head's endpoint columns so they always reflect the latest commit
      // (a re-committed relation may have resolved new endpoints). Nulls are valid (FU4).
      if ("relation".equals(cmd.entityType())) {
        jdbcTemplate.update(
            "UPDATE relations SET source_entity_id = ?, source_entity_type = ?,"
                + " target_entity_id = ?, target_entity_type = ? WHERE id = ?",
            cmd.sourceEntityId(),
            cmd.sourceEntityType(),
            cmd.targetEntityId(),
            cmd.targetEntityType(),
            entityId);
      } else if ("event".equals(cmd.entityType())) {
        // Refresh the event head's structured links so they reflect the latest commit (F4.6.4).
        jdbcTemplate.update(
            "UPDATE events SET space_id = ?, event_type = ? WHERE id = ?",
            cmd.spaceId(),
            cmd.eventType(),
            entityId);
      }
    }

    // Re-sync the event's involved-actor links to the latest resolved set (F4.6.4). The DELETE is a
    // harmless no-op on a brand-new event; ON CONFLICT guards a mention list with duplicate ids.
    if ("event".equals(cmd.entityType())) {
      jdbcTemplate.update("DELETE FROM event_involved_actors WHERE event_id = ?", entityId);
      for (UUID actorId : cmd.involvedActorIds()) {
        jdbcTemplate.update(
            "INSERT INTO event_involved_actors (event_id, actor_id) VALUES (?, ?)"
                + " ON CONFLICT DO NOTHING",
            entityId,
            actorId);
      }
    }

    jdbcTemplate.update(
        "INSERT INTO "
            + tables.versionTable()
            + " (id, "
            + tables.fkColumn()
            + ", session_id, version_number, changed_fields, full_snapshot, created_at)"
            + " VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, now())",
        entityVersionId,
        entityId,
        cmd.sessionId(),
        versionNumber,
        changedFieldsJson,
        fullSnapshotJson);

    return new CommittedEntityVersion(
        cmd.entityType(), entityId, entityVersionId, versionNumber, contentToEmbed, contentHash);
  }

  @Override
  public Optional<ResolvedEndpoint> findEndpointByName(UUID campaignId, String name) {
    if (name == null || name.isBlank()) {
      return Optional.empty();
    }
    // Actors take precedence over spaces; the first case-insensitive match wins (D-095).
    return findEndpoint("actors", "actor", campaignId, name)
        .or(() -> findEndpoint("spaces", "space", campaignId, name));
  }

  private Optional<ResolvedEndpoint> findEndpoint(
      String headTable, String entityType, UUID campaignId, String name) {
    List<UUID> ids =
        jdbcTemplate.query(
            "SELECT id FROM "
                + headTable
                + " WHERE campaign_id = ? AND lower(name) = lower(?) ORDER BY created_at ASC"
                + " LIMIT 1",
            (rs, rowNum) -> rs.getObject("id", UUID.class),
            campaignId,
            name);
    return ids.isEmpty()
        ? Optional.empty()
        : Optional.of(new ResolvedEndpoint(ids.get(0), entityType));
  }

  @Override
  public Optional<UUID> findEntityIdByName(UUID campaignId, String name, String entityType) {
    if (name == null || name.isBlank()) {
      return Optional.empty();
    }
    // Only actors/spaces are name-matchable event links; reuse the head-table resolver.
    String headTable =
        switch (entityType) {
          case "actor" -> "actors";
          case "space" -> "spaces";
          default ->
              throw new IllegalArgumentException("Unsupported link entity type: " + entityType);
        };
    return findEndpoint(headTable, entityType, campaignId, name).map(ResolvedEndpoint::entityId);
  }

  @Override
  public boolean existsInCampaign(String entityType, UUID entityId, UUID campaignId) {
    TablePair tables = resolveTablePair(entityType);
    Boolean exists =
        jdbcTemplate.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM "
                + tables.headTable()
                + " WHERE id = ? AND campaign_id = ?)",
            Boolean.class,
            entityId,
            campaignId);
    return Boolean.TRUE.equals(exists);
  }

  private String toJson(Map<String, Object> map) {
    if (map == null || map.isEmpty()) {
      return "{}";
    }
    try {
      return objectMapper.writeValueAsString(map);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Failed to serialize map to JSON", e);
    }
  }

  private static String sha256Hex(String content) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
