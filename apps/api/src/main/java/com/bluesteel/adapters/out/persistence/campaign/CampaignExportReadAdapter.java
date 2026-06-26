package com.bluesteel.adapters.out.persistence.campaign;

import com.bluesteel.application.model.campaign.ArchivedAnnotation;
import com.bluesteel.application.model.campaign.ArchivedEntity;
import com.bluesteel.application.model.campaign.ArchivedEntityVersion;
import com.bluesteel.application.model.campaign.ArchivedSession;
import com.bluesteel.application.port.out.campaign.CampaignExportReadPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Native-SQL implementation of {@link CampaignExportReadPort} (D-062, D-112). Bulk-reads the four
 * world-state head/version table pairs plus annotations and sessions with set-based queries (no
 * N+1) and an explicit per-statement fetch size so the JDBC driver streams rows rather than
 * buffering the whole result set — keeping export within the Render free-tier memory budget.
 * Mirrors the routing/JSONB-cast conventions of {@link
 * com.bluesteel.adapters.out.persistence.worldstate.WorldStateReadAdapter}: entity type is resolved
 * against a closed whitelist before any table name is interpolated.
 */
@Component
public class CampaignExportReadAdapter implements CampaignExportReadPort {

  private static final List<String> ENTITY_TYPES = List.of("actor", "space", "event", "relation");

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;
  private final int fetchSize;

  public CampaignExportReadAdapter(
      @Lazy JdbcTemplate jdbcTemplate,
      ObjectMapper objectMapper,
      @Value("${campaign.export.fetch-size:200}") int fetchSize) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
    this.fetchSize = fetchSize;
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
  public long countEntities(UUID campaignId) {
    Long count =
        jdbcTemplate.queryForObject(
            """
            SELECT (SELECT COUNT(*) FROM actors WHERE campaign_id = ?)
                 + (SELECT COUNT(*) FROM spaces WHERE campaign_id = ?)
                 + (SELECT COUNT(*) FROM events WHERE campaign_id = ?)
                 + (SELECT COUNT(*) FROM relations WHERE campaign_id = ?)
            """,
            Long.class,
            campaignId,
            campaignId,
            campaignId,
            campaignId);
    return count == null ? 0L : count;
  }

  @Override
  public List<ArchivedEntity> readEntities(UUID campaignId) {
    List<ArchivedEntity> entities = new ArrayList<>();
    for (String entityType : ENTITY_TYPES) {
      entities.addAll(readEntitiesOfType(entityType, campaignId));
    }
    return entities;
  }

  private List<ArchivedEntity> readEntitiesOfType(String entityType, UUID campaignId) {
    TablePair tables = resolveTablePair(entityType);
    String sql =
        """
        SELECT h.id AS entity_id,
               h.name,
               h.owner_id,
               h.created_at AS entity_created_at,
               v.id AS version_id,
               v.version_number,
               v.session_id,
               v.changed_fields::text AS changed_fields,
               v.full_snapshot::text AS full_snapshot,
               v.created_at AS version_created_at
        FROM %s h
        JOIN %s v ON v.%s = h.id
        WHERE h.campaign_id = ?
        ORDER BY h.id ASC, v.version_number ASC
        """
            .formatted(tables.headTable(), tables.versionTable(), tables.fkColumn());

    List<EntityVersionRow> rows =
        jdbcTemplate.query(
            connection -> {
              PreparedStatement ps = connection.prepareStatement(sql);
              ps.setFetchSize(fetchSize);
              ps.setObject(1, campaignId);
              return ps;
            },
            (rs, rowNum) -> mapEntityVersionRow(rs));

    return groupByEntity(entityType, rows);
  }

  private List<ArchivedEntity> groupByEntity(String entityType, List<EntityVersionRow> rows) {
    List<ArchivedEntity> entities = new ArrayList<>();
    UUID currentId = null;
    List<ArchivedEntityVersion> versions = null;
    EntityVersionRow head = null;

    for (EntityVersionRow row : rows) {
      if (!row.entityId().equals(currentId)) {
        if (currentId != null) {
          entities.add(toArchivedEntity(entityType, head, versions));
        }
        currentId = row.entityId();
        head = row;
        versions = new ArrayList<>();
      }
      versions.add(
          new ArchivedEntityVersion(
              row.versionId(),
              row.versionNumber(),
              row.sessionId(),
              row.changedFields(),
              row.fullSnapshot(),
              row.versionCreatedAt()));
    }
    if (currentId != null) {
      entities.add(toArchivedEntity(entityType, head, versions));
    }
    return entities;
  }

  private static ArchivedEntity toArchivedEntity(
      String entityType, EntityVersionRow head, List<ArchivedEntityVersion> versions) {
    return new ArchivedEntity(
        entityType, head.entityId(), head.name(), head.ownerId(), head.entityCreatedAt(), versions);
  }

  @Override
  public List<ArchivedAnnotation> readAnnotations(UUID campaignId) {
    String sql =
        """
        SELECT id, entity_type, entity_id, author_id, content, created_at
        FROM annotations
        WHERE campaign_id = ?
        ORDER BY created_at ASC, id ASC
        """;
    return jdbcTemplate.query(
        connection -> {
          PreparedStatement ps = connection.prepareStatement(sql);
          ps.setFetchSize(fetchSize);
          ps.setObject(1, campaignId);
          return ps;
        },
        (rs, rowNum) ->
            new ArchivedAnnotation(
                rs.getObject("id", UUID.class),
                rs.getString("entity_type"),
                rs.getObject("entity_id", UUID.class),
                rs.getObject("author_id", UUID.class),
                rs.getString("content"),
                instant(rs, "created_at")));
  }

  @Override
  public List<ArchivedSession> readSessions(UUID campaignId) {
    String sql =
        """
        SELECT id, owner_id, sequence_number, status, committed_at, created_at
        FROM sessions
        WHERE campaign_id = ?
        ORDER BY created_at ASC, id ASC
        """;
    return jdbcTemplate.query(
        connection -> {
          PreparedStatement ps = connection.prepareStatement(sql);
          ps.setFetchSize(fetchSize);
          ps.setObject(1, campaignId);
          return ps;
        },
        (rs, rowNum) ->
            new ArchivedSession(
                rs.getObject("id", UUID.class),
                rs.getObject("owner_id", UUID.class),
                rs.getObject("sequence_number", Integer.class),
                rs.getString("status"),
                instant(rs, "committed_at"),
                instant(rs, "created_at")));
  }

  private EntityVersionRow mapEntityVersionRow(ResultSet rs) throws SQLException {
    return new EntityVersionRow(
        rs.getObject("entity_id", UUID.class),
        rs.getString("name"),
        rs.getObject("owner_id", UUID.class),
        instant(rs, "entity_created_at"),
        rs.getObject("version_id", UUID.class),
        rs.getInt("version_number"),
        rs.getObject("session_id", UUID.class),
        parseJson(rs.getString("changed_fields")),
        parseJson(rs.getString("full_snapshot")),
        instant(rs, "version_created_at"));
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }

  private Map<String, Object> parseJson(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to parse world-state JSONB snapshot", e);
    }
  }

  private record EntityVersionRow(
      UUID entityId,
      String name,
      UUID ownerId,
      Instant entityCreatedAt,
      UUID versionId,
      int versionNumber,
      UUID sessionId,
      Map<String, Object> changedFields,
      Map<String, Object> fullSnapshot,
      Instant versionCreatedAt) {}
}
