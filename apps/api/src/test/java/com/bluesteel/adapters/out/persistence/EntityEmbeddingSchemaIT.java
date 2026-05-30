package com.bluesteel.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.bluesteel.TestcontainersPostgresBaseIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Verifies that the F2.1.4 entity_embeddings schema changeset applies correctly (D-062, D-088). */
@DisplayName("Entity embeddings schema migration (F2.1.4)")
class EntityEmbeddingSchemaIT extends TestcontainersPostgresBaseIT {

  @Autowired private JdbcTemplate jdbcTemplate;

  // -------------------------------------------------------------------------
  // Table existence
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("should create the entity_embeddings table")
  void entityEmbeddingsTableExists() {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name = 'entity_embeddings'",
            Long.class);
    assertThat(count).isEqualTo(1L);
  }

  // -------------------------------------------------------------------------
  // Vector column (D-062)
  // -------------------------------------------------------------------------

  @Test
  @DisplayName(
      "should have an embedding column with vector type — dimension is not asserted (D-062, D-088)")
  void embeddingColumnIsVectorType() {
    // The dimension is a Liquibase parameter — do not hard-assert it.
    Long count =
        jdbcTemplate.queryForObject(
            """
            SELECT count(*) FROM information_schema.columns
            WHERE table_name = 'entity_embeddings'
              AND column_name = 'embedding'
              AND udt_name = 'vector'
            """,
            Long.class);
    assertThat(count).isEqualTo(1L);
  }

  // -------------------------------------------------------------------------
  // IVFFlat index
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("should have an IVFFlat index on the embedding column")
  void ivfFlatIndexExists() {
    Long count =
        jdbcTemplate.queryForObject(
            """
            SELECT count(*) FROM pg_indexes
            WHERE tablename = 'entity_embeddings'
              AND indexdef ILIKE '%ivfflat%'
            """,
            Long.class);
    assertThat(count).isGreaterThanOrEqualTo(1L);
  }

  // -------------------------------------------------------------------------
  // entity_type CHECK constraint
  // Verified via pg_constraint rather than INSERT because the embedding column
  // dimension is parameterized and inserting a vector of the wrong size would fail.
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("should have a CHECK constraint on entity_type")
  void entityTypeCheckConstraintExists() {
    Long count =
        jdbcTemplate.queryForObject(
            """
            SELECT count(*) FROM pg_constraint c
            JOIN pg_class t ON c.conrelid = t.oid
            WHERE t.relname = 'entity_embeddings'
              AND c.contype = 'c'
              AND c.conname = 'chk_entity_embeddings_entity_type'
            """,
            Long.class);
    assertThat(count).isEqualTo(1L);
  }
}
