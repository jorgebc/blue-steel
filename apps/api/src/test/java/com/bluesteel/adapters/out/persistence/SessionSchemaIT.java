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

/** Verifies that the F2.1.1 session schema changesets apply correctly (D-054, D-069). */
@DisplayName("Session schema migration (F2.1.1)")
class SessionSchemaIT extends TestcontainersPostgresBaseIT {

  @Autowired private JdbcTemplate jdbcTemplate;

  // -------------------------------------------------------------------------
  // Table existence
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("should create the sessions table")
  void sessionsTableExists() {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name = 'sessions'",
            Long.class);
    assertThat(count).isEqualTo(1L);
  }

  @Test
  @DisplayName("should create the narrative_blocks table")
  void narrativeBlocksTableExists() {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name = 'narrative_blocks'",
            Long.class);
    assertThat(count).isEqualTo(1L);
  }

  // -------------------------------------------------------------------------
  // Status CHECK constraint
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("should reject an invalid session status via CHECK constraint")
  void sessionStatusCheckConstraintEnforced() {
    UUID userId = insertUser("status-check@test.com");
    UUID campaignId = insertCampaign(userId);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO sessions (id, campaign_id, owner_id, status, created_at, updated_at)
                    VALUES (?, ?, ?, 'invalid_status', now(), now())
                    """,
                    UUID.randomUUID(),
                    campaignId,
                    userId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("should accept all valid session statuses")
  void sessionStatusAcceptsAllValidValues() {
    UUID userId = insertUser("status-valid@test.com");

    // 'processing' and 'draft' are covered by the partial-index tests; each needs its own campaign
    // to avoid violating sessions_one_active_per_campaign (D-054).
    for (String status :
        new String[] {"pending", "processing", "draft", "committed", "failed", "discarded"}) {
      UUID campaignId = insertCampaign(userId);
      jdbcTemplate.update(
          """
          INSERT INTO sessions (id, campaign_id, owner_id, status, created_at, updated_at)
          VALUES (?, ?, ?, ?, now(), now())
          """,
          UUID.randomUUID(),
          campaignId,
          userId,
          status);
    }
  }

  // -------------------------------------------------------------------------
  // D-054: single active session per campaign (partial unique index)
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("should block a second processing session for the same campaign (D-054)")
  void partialIndexBlocksSecondProcessingSession() {
    UUID userId = insertUser("active-block@test.com");
    UUID campaignId = insertCampaign(userId);

    jdbcTemplate.update(
        """
        INSERT INTO sessions (id, campaign_id, owner_id, status, created_at, updated_at)
        VALUES (?, ?, ?, 'processing', now(), now())
        """,
        UUID.randomUUID(),
        campaignId,
        userId);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO sessions (id, campaign_id, owner_id, status, created_at, updated_at)
                    VALUES (?, ?, ?, 'processing', now(), now())
                    """,
                    UUID.randomUUID(),
                    campaignId,
                    userId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("should block a second draft session for the same campaign (D-054)")
  void partialIndexBlocksSecondDraftSession() {
    UUID userId = insertUser("draft-block@test.com");
    UUID campaignId = insertCampaign(userId);

    jdbcTemplate.update(
        """
        INSERT INTO sessions (id, campaign_id, owner_id, status, created_at, updated_at)
        VALUES (?, ?, ?, 'draft', now(), now())
        """,
        UUID.randomUUID(),
        campaignId,
        userId);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO sessions (id, campaign_id, owner_id, status, created_at, updated_at)
                    VALUES (?, ?, ?, 'draft', now(), now())
                    """,
                    UUID.randomUUID(),
                    campaignId,
                    userId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("should allow a new active session once the previous one is committed (D-054)")
  void partialIndexAllowsNewSessionAfterCommit() {
    UUID userId = insertUser("committed-allow@test.com");
    UUID campaignId = insertCampaign(userId);
    UUID firstSessionId = UUID.randomUUID();

    jdbcTemplate.update(
        """
        INSERT INTO sessions (id, campaign_id, owner_id, status, created_at, updated_at)
        VALUES (?, ?, ?, 'processing', now(), now())
        """,
        firstSessionId,
        campaignId,
        userId);

    jdbcTemplate.update(
        "UPDATE sessions SET status = 'committed', committed_at = now(), sequence_number = 1 WHERE id = ?",
        firstSessionId);

    jdbcTemplate.update(
        """
        INSERT INTO sessions (id, campaign_id, owner_id, status, created_at, updated_at)
        VALUES (?, ?, ?, 'processing', now(), now())
        """,
        UUID.randomUUID(),
        campaignId,
        userId);

    Long count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM sessions WHERE campaign_id = ? AND status = 'processing'",
            Long.class,
            campaignId);
    assertThat(count).isEqualTo(1L);
  }

  // -------------------------------------------------------------------------
  // D-069: NULL sequence_numbers are not treated as duplicates
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("should allow multiple sessions with NULL sequence_number for same campaign (D-069)")
  void nullSequenceNumbersNotTreatedAsDuplicates() {
    UUID userId = insertUser("null-seq@test.com");
    UUID campaignId = insertCampaign(userId);

    jdbcTemplate.update(
        """
        INSERT INTO sessions (id, campaign_id, owner_id, status, created_at, updated_at)
        VALUES (?, ?, ?, 'committed', now(), now())
        """,
        UUID.randomUUID(),
        campaignId,
        userId);

    jdbcTemplate.update(
        """
        INSERT INTO sessions (id, campaign_id, owner_id, status, created_at, updated_at)
        VALUES (?, ?, ?, 'failed', now(), now())
        """,
        UUID.randomUUID(),
        campaignId,
        userId);

    Long count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM sessions WHERE campaign_id = ? AND sequence_number IS NULL",
            Long.class,
            campaignId);
    assertThat(count).isEqualTo(2L);
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
