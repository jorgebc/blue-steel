package com.bluesteel.adapters.out.persistence.worldstate;

import com.bluesteel.application.model.worldstate.EntityVersionView;
import com.bluesteel.application.model.worldstate.RelationDetailView;
import com.bluesteel.application.model.worldstate.RelationSummaryView;
import com.bluesteel.application.port.out.worldstate.RelationReadPort;
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
 * Native-SQL implementation of {@link RelationReadPort} (D-089). Reads the structured endpoint
 * columns added in migration 0023 from the {@code relations} head and the {@code kind} from the
 * latest {@code relation_versions} snapshot. Mirrors the JSONB-cast conventions of {@link
 * WorldStateReadAdapter}.
 */
@Component
public class RelationReadAdapter implements RelationReadPort {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public RelationReadAdapter(@Lazy JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  @Override
  public List<RelationSummaryView> list(UUID campaignId, UUID actorFilter) {
    String baseSql =
        """
        SELECT h.id AS relation_id,
               h.name,
               h.source_entity_id,
               h.source_entity_type,
               h.target_entity_id,
               h.target_entity_type,
               h.created_in_session_id,
               h.created_at,
               v.full_snapshot::text AS full_snapshot
        FROM relations h
        JOIN relation_versions v ON v.relation_id = h.id
        WHERE h.campaign_id = ?
          AND v.version_number = (
              SELECT MAX(v2.version_number) FROM relation_versions v2 WHERE v2.relation_id = h.id)
        """;

    if (actorFilter == null) {
      return jdbcTemplate.query(
          baseSql + " ORDER BY h.name ASC, h.id ASC", (rs, rowNum) -> mapSummary(rs), campaignId);
    }
    return jdbcTemplate.query(
        baseSql
            + " AND (h.source_entity_id = ? OR h.target_entity_id = ?)"
            + " ORDER BY h.name ASC, h.id ASC",
        (rs, rowNum) -> mapSummary(rs),
        campaignId,
        actorFilter,
        actorFilter);
  }

  @Override
  public RelationDetailView getWithHistory(UUID campaignId, UUID relationId) {
    List<HeadRow> heads =
        jdbcTemplate.query(
            """
            SELECT h.id,
                   h.name,
                   h.owner_id,
                   h.created_at,
                   h.source_entity_id,
                   h.source_entity_type,
                   h.target_entity_id,
                   h.target_entity_type
            FROM relations h
            WHERE h.id = ? AND h.campaign_id = ?
            """,
            (rs, rowNum) -> mapHead(rs),
            relationId,
            campaignId);

    if (heads.isEmpty()) {
      return null;
    }
    HeadRow head = heads.get(0);

    List<EntityVersionView> versions =
        jdbcTemplate.query(
            """
            SELECT v.id AS version_id,
                   v.version_number,
                   v.session_id,
                   s.sequence_number,
                   v.changed_fields::text AS changed_fields,
                   v.full_snapshot::text AS full_snapshot,
                   v.created_at
            FROM relation_versions v
            LEFT JOIN sessions s ON s.id = v.session_id
            WHERE v.relation_id = ?
            ORDER BY v.version_number ASC
            """,
            (rs, rowNum) -> mapVersion(rs),
            relationId);

    String kind = versions.isEmpty() ? null : kindOf(versions.get(versions.size() - 1));

    return new RelationDetailView(
        head.id(),
        head.name(),
        kind,
        head.sourceEntityId(),
        head.sourceEntityType(),
        head.targetEntityId(),
        head.targetEntityType(),
        head.ownerId(),
        head.createdAt(),
        versions);
  }

  private RelationSummaryView mapSummary(ResultSet rs) throws SQLException {
    Map<String, Object> snapshot = parseJson(rs.getString("full_snapshot"));
    Object kind = snapshot.get("kind");
    return new RelationSummaryView(
        rs.getObject("relation_id", UUID.class),
        rs.getString("name"),
        kind == null ? null : kind.toString(),
        rs.getObject("source_entity_id", UUID.class),
        rs.getString("source_entity_type"),
        rs.getObject("target_entity_id", UUID.class),
        rs.getString("target_entity_type"),
        rs.getObject("created_in_session_id", UUID.class),
        instant(rs, "created_at"));
  }

  private HeadRow mapHead(ResultSet rs) throws SQLException {
    return new HeadRow(
        rs.getObject("id", UUID.class),
        rs.getString("name"),
        rs.getObject("owner_id", UUID.class),
        instant(rs, "created_at"),
        rs.getObject("source_entity_id", UUID.class),
        rs.getString("source_entity_type"),
        rs.getObject("target_entity_id", UUID.class),
        rs.getString("target_entity_type"));
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

  private static String kindOf(EntityVersionView version) {
    Object kind = version.fullSnapshot().get("kind");
    return kind == null ? null : kind.toString();
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
      throw new IllegalStateException("Failed to parse relation JSONB snapshot", e);
    }
  }

  private record HeadRow(
      UUID id,
      String name,
      UUID ownerId,
      Instant createdAt,
      UUID sourceEntityId,
      String sourceEntityType,
      UUID targetEntityId,
      String targetEntityType) {}
}
