package com.bluesteel.adapters.out.persistence.worldstate;

import com.bluesteel.application.model.worldstate.CommittedEntityVersion;
import com.bluesteel.application.model.worldstate.EntityWriteCommand;
import com.bluesteel.application.port.out.worldstate.WorldStatePort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
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
