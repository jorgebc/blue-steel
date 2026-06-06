package com.bluesteel.adapters.out.persistence.worldstate;

import com.bluesteel.application.model.worldstate.EntityLinks;
import com.bluesteel.application.model.worldstate.EntitySummaryView;
import com.bluesteel.application.model.worldstate.RelationSummaryView;
import com.bluesteel.application.model.worldstate.TimelineEntryView;
import com.bluesteel.application.port.out.worldstate.EntityLinksReadPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Native-SQL implementation of {@link EntityLinksReadPort} (D-062, D-089). Assembles an entity's
 * profile cross-links from the structured links written at commit (F4.3, F4.6): relations
 * referencing either endpoint, the resolved entities on the other side, events occurring in a space
 * or involving an actor, and the distinct sessions the entity appears in. Mirrors the
 * latest-version subquery and JSONB-cast conventions of {@link RelationReadAdapter} and {@link
 * TimelineReadAdapter}.
 */
@Component
public class EntityLinksReadAdapter implements EntityLinksReadPort {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public EntityLinksReadAdapter(@Lazy JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  private record TablePair(String headTable, String versionTable, String fkColumn) {}

  private static TablePair resolveTablePair(String entityType) {
    return switch (entityType) {
      case "actor" -> new TablePair("actors", "actor_versions", "actor_id");
      case "space" -> new TablePair("spaces", "space_versions", "space_id");
      default ->
          throw new IllegalArgumentException("Unsupported entity type for links: " + entityType);
    };
  }

  /** Distinct opposite endpoint of a relation, preserved as (id, type) for resolution. */
  private record EndpointRef(UUID entityId, String entityType) {}

  @Override
  public EntityLinks getLinks(String entityType, UUID campaignId, UUID entityId) {
    TablePair tables = resolveTablePair(entityType);

    List<RelationSummaryView> relations = relations(campaignId, entityId);
    List<EntitySummaryView> relatedEntities = relatedEntities(campaignId, entityId, relations);
    List<TimelineEntryView> events = events(entityType, campaignId, entityId);
    List<UUID> appearanceSessionIds = appearanceSessionIds(tables, entityId);

    return new EntityLinks(relations, relatedEntities, events, appearanceSessionIds);
  }

  // -------------------------------------------------------------------------
  // Relations referencing the entity on either endpoint (latest version each)
  // -------------------------------------------------------------------------

  private List<RelationSummaryView> relations(UUID campaignId, UUID entityId) {
    return jdbcTemplate.query(
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
          AND (h.source_entity_id = ? OR h.target_entity_id = ?)
          AND v.version_number = (
              SELECT MAX(v2.version_number) FROM relation_versions v2 WHERE v2.relation_id = h.id)
        ORDER BY h.name ASC, h.id ASC
        """,
        (rs, rowNum) -> mapRelationSummary(rs),
        campaignId,
        entityId,
        entityId);
  }

  // -------------------------------------------------------------------------
  // Distinct entities at the other end of those relations, resolved to summaries
  // -------------------------------------------------------------------------

  private List<EntitySummaryView> relatedEntities(
      UUID campaignId, UUID entityId, List<RelationSummaryView> relations) {
    LinkedHashSet<EndpointRef> opposites = new LinkedHashSet<>();
    for (RelationSummaryView relation : relations) {
      EndpointRef opposite = oppositeEndpoint(entityId, relation);
      if (opposite != null) {
        opposites.add(opposite);
      }
    }

    List<EntitySummaryView> related = new ArrayList<>();
    for (EndpointRef ref : opposites) {
      EntitySummaryView summary = resolveSummary(campaignId, ref);
      if (summary != null) {
        related.add(summary);
      }
    }
    return related;
  }

  private static EndpointRef oppositeEndpoint(UUID entityId, RelationSummaryView relation) {
    if (entityId.equals(relation.sourceEntityId())
        && relation.targetEntityId() != null
        && !entityId.equals(relation.targetEntityId())) {
      return new EndpointRef(relation.targetEntityId(), relation.targetEntityType());
    }
    if (entityId.equals(relation.targetEntityId())
        && relation.sourceEntityId() != null
        && !entityId.equals(relation.sourceEntityId())) {
      return new EndpointRef(relation.sourceEntityId(), relation.sourceEntityType());
    }
    return null;
  }

  private EntitySummaryView resolveSummary(UUID campaignId, EndpointRef ref) {
    TablePair tables = resolveTablePair(ref.entityType());
    String sql =
        """
        SELECT h.id AS entity_id,
               h.name,
               h.created_at,
               v.version_number,
               v.session_id,
               v.full_snapshot::text AS full_snapshot
        FROM %s h
        JOIN %s v ON v.%s = h.id
        WHERE h.id = ? AND h.campaign_id = ?
          AND v.version_number = (
              SELECT MAX(v2.version_number) FROM %s v2 WHERE v2.%s = h.id)
        """
            .formatted(
                tables.headTable(),
                tables.versionTable(),
                tables.fkColumn(),
                tables.versionTable(),
                tables.fkColumn());

    List<EntitySummaryView> rows =
        jdbcTemplate.query(
            sql, (rs, rowNum) -> mapSummary(rs, ref.entityType()), ref.entityId(), campaignId);
    return rows.isEmpty() ? null : rows.get(0);
  }

  // -------------------------------------------------------------------------
  // Events linked to the entity: actors via involvement, spaces via location
  // -------------------------------------------------------------------------

  private List<TimelineEntryView> events(String entityType, UUID campaignId, UUID entityId) {
    String base =
        """
        SELECT e.id AS event_id,
               e.name,
               e.event_type,
               e.created_at,
               sp.name AS space_name,
               ev.full_snapshot::text AS full_snapshot,
               ev.session_id,
               s.sequence_number AS session_seq,
               (SELECT array_agg(a.name ORDER BY a.name)
                  FROM event_involved_actors ea
                  JOIN actors a ON a.id = ea.actor_id
                 WHERE ea.event_id = e.id) AS involved_actors
        FROM events e
        JOIN event_versions ev ON ev.event_id = e.id
        JOIN sessions s ON s.id = ev.session_id
        LEFT JOIN spaces sp ON sp.id = e.space_id
        WHERE e.campaign_id = ?
          AND s.status = 'committed'
          AND ev.version_number = (
              SELECT MAX(v2.version_number) FROM event_versions v2 WHERE v2.event_id = e.id)
        """;
    String filter =
        switch (entityType) {
          case "space" -> " AND e.space_id = ?";
          case "actor" ->
              " AND EXISTS (SELECT 1 FROM event_involved_actors eia"
                  + " WHERE eia.event_id = e.id AND eia.actor_id = ?)";
          default ->
              throw new IllegalArgumentException(
                  "Unsupported entity type for links: " + entityType);
        };

    return jdbcTemplate.query(
        base + filter + " ORDER BY s.sequence_number ASC, e.id ASC",
        (rs, rowNum) -> mapEntry(rs),
        campaignId,
        entityId);
  }

  // -------------------------------------------------------------------------
  // Distinct sessions the entity appears in, ordered by first appearance
  // -------------------------------------------------------------------------

  private List<UUID> appearanceSessionIds(TablePair tables, UUID entityId) {
    String sql =
        """
        SELECT v.session_id
        FROM %s v
        WHERE v.%s = ?
        GROUP BY v.session_id
        ORDER BY MIN(v.version_number) ASC
        """
            .formatted(tables.versionTable(), tables.fkColumn());
    return jdbcTemplate.query(
        sql, (rs, rowNum) -> rs.getObject("session_id", UUID.class), entityId);
  }

  // -------------------------------------------------------------------------
  // Row mappers (mirrored from RelationReadAdapter / TimelineReadAdapter)
  // -------------------------------------------------------------------------

  private RelationSummaryView mapRelationSummary(ResultSet rs) throws SQLException {
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

  private TimelineEntryView mapEntry(ResultSet rs) throws SQLException {
    Map<String, Object> snapshot = parseJson(rs.getString("full_snapshot"));
    return new TimelineEntryView(
        rs.getObject("event_id", UUID.class),
        rs.getString("name"),
        rs.getString("event_type"),
        involvedActorNames(rs.getArray("involved_actors")),
        rs.getString("space_name"),
        rs.getObject("session_id", UUID.class),
        rs.getObject("session_seq", Integer.class),
        snapshot,
        instant(rs, "created_at"));
  }

  private static List<String> involvedActorNames(java.sql.Array array) throws SQLException {
    if (array == null) {
      return List.of();
    }
    String[] names = (String[]) array.getArray();
    return java.util.Arrays.stream(names).filter(Objects::nonNull).toList();
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
}
