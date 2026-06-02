package com.bluesteel.adapters.out.persistence.worldstate;

import com.bluesteel.application.model.worldstate.EntityDetailView;
import com.bluesteel.application.model.worldstate.EntityListFilter;
import com.bluesteel.application.model.worldstate.EntityListPage;
import com.bluesteel.application.model.worldstate.EntitySummaryView;
import com.bluesteel.application.model.worldstate.EntityVersionView;
import com.bluesteel.application.port.out.worldstate.WorldStateReadPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Native-SQL implementation of {@link WorldStateReadPort} over the head/version table pairs
 * (D-089). Mirrors the routing and JSONB-cast conventions of {@link
 * com.bluesteel.adapters.out.persistence.worldstate.WorldStateAdapter}: entity type is resolved
 * against a closed whitelist before any table name is interpolated, and snapshots are read as
 * {@code ::text} then parsed back into maps (D-062).
 */
@Component
public class WorldStateReadAdapter implements WorldStateReadPort {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public WorldStateReadAdapter(@Lazy JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
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
  public EntityListPage list(
      String entityType, UUID campaignId, EntityListFilter filter, int page, int size) {
    TablePair tables = resolveTablePair(entityType);
    String nameContains = filter == null ? null : filter.nameContains();
    String namePattern = nameContains == null ? null : "%" + nameContains + "%";

    String nameClause = namePattern == null ? "" : " AND h.name ILIKE ?";

    long totalCount =
        namePattern == null
            ? jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tables.headTable() + " h WHERE h.campaign_id = ?",
                Long.class,
                campaignId)
            : jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM "
                    + tables.headTable()
                    + " h WHERE h.campaign_id = ?"
                    + nameClause,
                Long.class,
                campaignId,
                namePattern);

    String listSql =
        """
        SELECT h.id AS entity_id,
               h.name,
               h.created_at,
               v.version_number,
               v.session_id,
               v.full_snapshot::text AS full_snapshot
        FROM %s h
        JOIN %s v ON v.%s = h.id
        WHERE h.campaign_id = ?
          AND v.version_number = (
              SELECT MAX(v2.version_number) FROM %s v2 WHERE v2.%s = h.id)
        %s
        ORDER BY h.name ASC, h.id ASC
        LIMIT ? OFFSET ?
        """
            .formatted(
                tables.headTable(),
                tables.versionTable(),
                tables.fkColumn(),
                tables.versionTable(),
                tables.fkColumn(),
                nameClause);

    int offset = page * size;
    List<EntitySummaryView> items =
        namePattern == null
            ? jdbcTemplate.query(
                listSql, (rs, rowNum) -> mapSummary(rs, entityType), campaignId, size, offset)
            : jdbcTemplate.query(
                listSql,
                (rs, rowNum) -> mapSummary(rs, entityType),
                campaignId,
                namePattern,
                size,
                offset);

    return new EntityListPage(items, page, size, totalCount == 0 ? 0L : totalCount);
  }

  @Override
  public EntityDetailView getWithHistory(String entityType, UUID campaignId, UUID entityId) {
    TablePair tables = resolveTablePair(entityType);

    List<HeadRow> heads =
        jdbcTemplate.query(
            "SELECT h.id, h.name, h.owner_id, h.created_at FROM "
                + tables.headTable()
                + " h WHERE h.id = ? AND h.campaign_id = ?",
            (rs, rowNum) ->
                new HeadRow(
                    rs.getObject("id", UUID.class),
                    rs.getString("name"),
                    rs.getObject("owner_id", UUID.class),
                    instant(rs, "created_at")),
            entityId,
            campaignId);

    if (heads.isEmpty()) {
      return null;
    }
    HeadRow head = heads.get(0);

    String versionSql =
        """
        SELECT v.id AS version_id,
               v.version_number,
               v.session_id,
               s.sequence_number,
               v.changed_fields::text AS changed_fields,
               v.full_snapshot::text AS full_snapshot,
               v.created_at
        FROM %s v
        LEFT JOIN sessions s ON s.id = v.session_id
        WHERE v.%s = ?
        ORDER BY v.version_number ASC
        """
            .formatted(tables.versionTable(), tables.fkColumn());

    List<EntityVersionView> versions =
        jdbcTemplate.query(versionSql, (rs, rowNum) -> mapVersion(rs), entityId);

    return new EntityDetailView(
        head.id(), entityType, head.name(), head.ownerId(), head.createdAt(), versions);
  }

  private EntitySummaryView mapSummary(ResultSet rs, String entityType) throws SQLException {
    return new EntitySummaryView(
        rs.getObject("entity_id", UUID.class),
        entityType,
        rs.getString("name"),
        rs.getInt("version_number"),
        parseJson(rs.getString("full_snapshot")),
        rs.getObject("session_id", UUID.class),
        instant(rs, "created_at"));
  }

  private EntityVersionView mapVersion(ResultSet rs) throws SQLException {
    Integer sequenceNumber = rs.getObject("sequence_number", Integer.class);
    return new EntityVersionView(
        rs.getObject("version_id", UUID.class),
        rs.getInt("version_number"),
        rs.getObject("session_id", UUID.class),
        sequenceNumber,
        parseJson(rs.getString("changed_fields")),
        parseJson(rs.getString("full_snapshot")),
        instant(rs, "created_at"));
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

  private record HeadRow(UUID id, String name, UUID ownerId, Instant createdAt) {}
}
