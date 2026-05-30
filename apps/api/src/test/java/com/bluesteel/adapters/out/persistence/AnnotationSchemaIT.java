package com.bluesteel.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bluesteel.TestcontainersPostgresBaseIT;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Verifies that the F2.1.5 annotations schema changeset applies correctly (D-021). */
@DisplayName("Annotations schema migration (F2.1.5)")
class AnnotationSchemaIT extends TestcontainersPostgresBaseIT {

  @Autowired private JdbcTemplate jdbcTemplate;

  // -------------------------------------------------------------------------
  // Table existence
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("should create the annotations table")
  void annotationsTableExists() {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name = 'annotations'",
            Long.class);
    assertThat(count).isEqualTo(1L);
  }

  // -------------------------------------------------------------------------
  // Immutability: no updated_at column
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("should NOT have an updated_at column — annotations are immutable")
  void annotationsHasNoUpdatedAtColumn() {
    Long count =
        jdbcTemplate.queryForObject(
            """
            SELECT count(*) FROM information_schema.columns
            WHERE table_name = 'annotations' AND column_name = 'updated_at'
            """,
            Long.class);
    assertThat(count).isZero();
  }

  // -------------------------------------------------------------------------
  // entity_type CHECK constraint
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("should reject an invalid entity_type via CHECK constraint")
  void entityTypeCheckConstraintEnforced() {
    UUID userId = insertUser("ann-check@test.com");
    UUID campaignId = insertCampaign(userId);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO annotations
                      (id, campaign_id, entity_type, entity_id, author_id, content, created_at)
                    VALUES (?, ?, 'invalid_type', ?, ?, 'Some note', now())
                    """,
                    UUID.randomUUID(),
                    campaignId,
                    UUID.randomUUID(),
                    userId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("should accept all valid entity_type values")
  void entityTypeAcceptsAllValidValues() {
    UUID userId = insertUser("ann-valid@test.com");
    UUID campaignId = insertCampaign(userId);

    for (String entityType : new String[] {"actor", "space", "relation", "event"}) {
      jdbcTemplate.update(
          """
          INSERT INTO annotations
            (id, campaign_id, entity_type, entity_id, author_id, content, created_at)
          VALUES (?, ?, ?, ?, ?, 'Note', now())
          """,
          UUID.randomUUID(),
          campaignId,
          entityType,
          UUID.randomUUID(),
          userId);
    }

    Long count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM annotations WHERE campaign_id = ?", Long.class, campaignId);
    assertThat(count).isEqualTo(4L);
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private UUID insertUser(String email) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO users (id, email, password_hash, is_admin, force_password_change, created_at) VALUES (?, ?, 'hash', FALSE, FALSE, now())",
        id,
        email);
    return id;
  }

  private UUID insertCampaign(UUID createdBy) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO campaigns (id, name, created_by, created_at) VALUES (?, 'Test Campaign', ?, now())",
        id,
        createdBy);
    return id;
  }
}
