package com.bluesteel.adapters.out.persistence.embedding;

import com.bluesteel.application.model.ingestion.SimilarityResult;
import com.bluesteel.application.port.out.ingestion.EntitySimilaritySearchPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Native pgvector similarity search for entity resolution Stage 1 (D-041, D-062). Uses cosine
 * distance ({@code <=>}) over the {@code entity_embeddings} table joined to the type-specific head
 * and version tables. Never uses Spring AI {@code VectorStore} (ARCH-04).
 */
@Component
public class EntitySimilaritySearchAdapter implements EntitySimilaritySearchPort {

  private final JdbcTemplate jdbcTemplate;

  public EntitySimilaritySearchAdapter(@Lazy JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public List<SimilarityResult> search(
      float[] queryVector, UUID campaignId, String entityType, int topN) {
    TablePair tables = resolveTablePair(entityType);
    String vectorLiteral = toVectorLiteral(queryVector);
    String sql = buildSql(tables);
    return jdbcTemplate.query(
        sql,
        (rs, rowNum) -> mapRow(rs),
        vectorLiteral,
        entityType,
        campaignId,
        vectorLiteral,
        topN);
  }

  private record TablePair(String headTable, String versionTable) {}

  private static TablePair resolveTablePair(String entityType) {
    return switch (entityType) {
      case "actor" -> new TablePair("actors", "actor_versions");
      case "space" -> new TablePair("spaces", "space_versions");
      case "event" -> new TablePair("events", "event_versions");
      case "relation" -> new TablePair("relations", "relation_versions");
      default -> throw new IllegalArgumentException("Unknown entity type: " + entityType);
    };
  }

  /**
   * Builds the native SQL query for the given table pair. Table names come exclusively from the
   * whitelisted {@link #resolveTablePair} switch — never from user input.
   */
  private static String buildSql(TablePair tables) {
    return """
        SELECT h.id AS entity_id,
               e.entity_type,
               h.name,
               v.full_snapshot::text AS state_snapshot,
               v.session_id,
               v.version_number,
               1 - (e.embedding <=> ?::vector) AS similarity
        FROM entity_embeddings e
        JOIN %s v ON v.id = e.entity_version_id
        JOIN %s h ON h.id = e.entity_id
        WHERE e.entity_type = ?
          AND h.campaign_id = ?
        ORDER BY e.embedding <=> ?::vector
        LIMIT ?
        """
        .formatted(tables.versionTable(), tables.headTable());
  }

  private static SimilarityResult mapRow(ResultSet rs) throws SQLException {
    return new SimilarityResult(
        rs.getObject("entity_id", UUID.class),
        rs.getString("entity_type"),
        rs.getString("name"),
        rs.getString("state_snapshot"),
        rs.getObject("session_id", UUID.class),
        rs.getInt("version_number"),
        rs.getDouble("similarity"));
  }

  /** Renders a {@code float[]} as a pgvector literal, e.g. {@code [1.0,0.0,...,0.0]}. */
  static String toVectorLiteral(float[] vector) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < vector.length; i++) {
      if (i > 0) sb.append(",");
      sb.append(vector[i]);
    }
    return sb.append("]").toString();
  }
}
