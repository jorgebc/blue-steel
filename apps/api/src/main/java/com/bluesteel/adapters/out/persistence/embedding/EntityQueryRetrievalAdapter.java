package com.bluesteel.adapters.out.persistence.embedding;

import com.bluesteel.application.model.ingestion.EntityContext;
import com.bluesteel.application.port.out.query.QueryContextRetrievalPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Native pgvector cross-type retrieval for Query Mode (D-062, D-063, D-034). A single {@code
 * JdbcTemplate} query LEFT JOINs {@code entity_embeddings} to the four {@code *_versions} tables
 * (for {@code full_snapshot} and {@code version_number}) and the four head tables (for {@code
 * name}) by the {@code entity_type} discriminator, joins {@code sessions} to scope the search to a
 * campaign's {@code committed} sessions, and orders by cosine distance ({@code <=>}). Starting FROM
 * {@code entity_embeddings} naturally excludes versions without an embedding (D-063). Decoupled
 * from the Stage-1 ingestion path ({@link EntitySimilaritySearchAdapter}). Never uses Spring AI
 * {@code VectorStore} (ARCH-04).
 */
@Component
public class EntityQueryRetrievalAdapter implements QueryContextRetrievalPort {

  // All table names below are hard-coded literals — never user input.
  private static final String SQL =
      """
      SELECT ee.entity_type,
             ee.entity_id,
             COALESCE(ah.name, sh.name, eh.name, rh.name) AS name,
             COALESCE(av.full_snapshot, sv.full_snapshot, ev.full_snapshot, rv.full_snapshot)::text
               AS state_snapshot,
             ee.session_id,
             COALESCE(av.version_number, sv.version_number, ev.version_number, rv.version_number)
               AS version_number
      FROM entity_embeddings ee
      JOIN sessions s ON s.id = ee.session_id
      LEFT JOIN actor_versions    av ON av.id = ee.entity_version_id AND ee.entity_type = 'actor'
      LEFT JOIN space_versions    sv ON sv.id = ee.entity_version_id AND ee.entity_type = 'space'
      LEFT JOIN event_versions    ev ON ev.id = ee.entity_version_id AND ee.entity_type = 'event'
      LEFT JOIN relation_versions rv ON rv.id = ee.entity_version_id AND ee.entity_type = 'relation'
      LEFT JOIN actors    ah ON ah.id = ee.entity_id AND ee.entity_type = 'actor'
      LEFT JOIN spaces    sh ON sh.id = ee.entity_id AND ee.entity_type = 'space'
      LEFT JOIN events    eh ON eh.id = ee.entity_id AND ee.entity_type = 'event'
      LEFT JOIN relations rh ON rh.id = ee.entity_id AND ee.entity_type = 'relation'
      WHERE s.campaign_id = ?
        AND s.status = 'committed'
      ORDER BY ee.embedding <=> ?::vector
      LIMIT ?
      """;

  private final JdbcTemplate jdbcTemplate;

  public EntityQueryRetrievalAdapter(@Lazy JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public List<EntityContext> retrieveRelevantContext(
      UUID campaignId, float[] queryEmbedding, int topN) {
    String vectorLiteral = EntitySimilaritySearchAdapter.toVectorLiteral(queryEmbedding);
    return jdbcTemplate.query(SQL, (rs, rowNum) -> mapRow(rs), campaignId, vectorLiteral, topN);
  }

  private static EntityContext mapRow(ResultSet rs) throws SQLException {
    return new EntityContext(
        rs.getObject("entity_id", UUID.class),
        rs.getString("entity_type"),
        rs.getString("name"),
        rs.getString("state_snapshot"),
        rs.getObject("session_id", UUID.class),
        rs.getInt("version_number"));
  }
}
