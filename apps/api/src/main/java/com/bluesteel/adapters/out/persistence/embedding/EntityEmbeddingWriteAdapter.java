package com.bluesteel.adapters.out.persistence.embedding;

import com.bluesteel.application.model.embedding.EntityEmbeddingRow;
import com.bluesteel.application.port.out.embedding.EntityEmbeddingWritePort;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Native-SQL INSERT into {@code entity_embeddings} (D-062, D-063). Uses the same {@code ?::vector}
 * cast pattern as {@link EntitySimilaritySearchAdapter} for pgvector compatibility.
 */
@Component
public class EntityEmbeddingWriteAdapter implements EntityEmbeddingWritePort {

  private final JdbcTemplate jdbcTemplate;

  public EntityEmbeddingWriteAdapter(@Lazy JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void insert(EntityEmbeddingRow row) {
    jdbcTemplate.update(
        "INSERT INTO entity_embeddings"
            + " (id, entity_type, entity_id, entity_version_id, session_id, embedding, content_hash, created_at)"
            + " VALUES (?, ?, ?, ?, ?, ?::vector, ?, now())",
        UUID.randomUUID(),
        row.entityType(),
        row.entityId(),
        row.entityVersionId(),
        row.sessionId(),
        toVectorLiteral(row.embedding()),
        row.contentHash());
  }

  static String toVectorLiteral(float[] vector) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < vector.length; i++) {
      if (i > 0) sb.append(",");
      sb.append(vector[i]);
    }
    return sb.append("]").toString();
  }
}
