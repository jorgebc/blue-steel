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

/** Verifies that the F2.1.2 actor + space schema changesets apply correctly (D-021, D-035). */
@DisplayName("Actor and space schema migration (F2.1.2)")
class WorldStateActorSpaceSchemaIT extends TestcontainersPostgresBaseIT {

  @Autowired private JdbcTemplate jdbcTemplate;

  // -------------------------------------------------------------------------
  // Table existence
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("should create the actors table")
  void actorsTableExists() {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name = 'actors'",
            Long.class);
    assertThat(count).isEqualTo(1L);
  }

  @Test
  @DisplayName("should create the actor_versions table")
  void actorVersionsTableExists() {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name = 'actor_versions'",
            Long.class);
    assertThat(count).isEqualTo(1L);
  }

  @Test
  @DisplayName("should create the spaces table")
  void spacesTableExists() {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name = 'spaces'",
            Long.class);
    assertThat(count).isEqualTo(1L);
  }

  @Test
  @DisplayName("should create the space_versions table")
  void spaceVersionsTableExists() {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name = 'space_versions'",
            Long.class);
    assertThat(count).isEqualTo(1L);
  }

  // -------------------------------------------------------------------------
  // D-021: campaign_id + owner_id on head tables
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("should have campaign_id and owner_id columns on actors (D-021)")
  void actorsHasCampaignAndOwnerColumns() {
    for (String col : new String[] {"campaign_id", "owner_id"}) {
      Long count =
          jdbcTemplate.queryForObject(
              "SELECT count(*) FROM information_schema.columns WHERE table_name = 'actors' AND column_name = ?",
              Long.class,
              col);
      assertThat(count).as("actors.%s", col).isEqualTo(1L);
    }
  }

  @Test
  @DisplayName("should have campaign_id and owner_id columns on spaces (D-021)")
  void spacesHasCampaignAndOwnerColumns() {
    for (String col : new String[] {"campaign_id", "owner_id"}) {
      Long count =
          jdbcTemplate.queryForObject(
              "SELECT count(*) FROM information_schema.columns WHERE table_name = 'spaces' AND column_name = ?",
              Long.class,
              col);
      assertThat(count).as("spaces.%s", col).isEqualTo(1L);
    }
  }

  // -------------------------------------------------------------------------
  // D-035: version FK enforcement
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("should enforce actor_versions.actor_id FK — orphan version must fail")
  void actorVersionsActorFkEnforced() {
    UUID userId = insertUser("actor-fk@test.com");
    UUID campaignId = insertCampaign(userId);
    UUID sessionId = insertSession(campaignId, userId);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO actor_versions
                      (id, actor_id, session_id, version_number, full_snapshot, created_at)
                    VALUES (?, ?, ?, 1, '{}', now())
                    """,
                    UUID.randomUUID(),
                    UUID.randomUUID(), // non-existent actor
                    sessionId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("should enforce space_versions.space_id FK — orphan version must fail")
  void spaceVersionsSpaceFkEnforced() {
    UUID userId = insertUser("space-fk@test.com");
    UUID campaignId = insertCampaign(userId);
    UUID sessionId = insertSession(campaignId, userId);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO space_versions
                      (id, space_id, session_id, version_number, full_snapshot, created_at)
                    VALUES (?, ?, ?, 1, '{}', now())
                    """,
                    UUID.randomUUID(),
                    UUID.randomUUID(), // non-existent space
                    sessionId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("should persist actor version when actor and session exist (D-035)")
  void actorVersionPersistsWhenRefsExist() {
    UUID userId = insertUser("actor-version-ok@test.com");
    UUID campaignId = insertCampaign(userId);
    UUID sessionId = insertSession(campaignId, userId);
    UUID actorId = insertActor(campaignId, userId, sessionId);

    jdbcTemplate.update(
        """
        INSERT INTO actor_versions
          (id, actor_id, session_id, version_number, full_snapshot, created_at)
        VALUES (?, ?, ?, 1, '{"name":"Hero"}', now())
        """,
        UUID.randomUUID(),
        actorId,
        sessionId);

    Long count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM actor_versions WHERE actor_id = ?", Long.class, actorId);
    assertThat(count).isEqualTo(1L);
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

  private UUID insertSession(UUID campaignId, UUID ownerId) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO sessions (id, campaign_id, owner_id, status, created_at, updated_at) VALUES (?, ?, ?, 'committed', now(), now())",
        id,
        campaignId,
        ownerId);
    return id;
  }

  private UUID insertActor(UUID campaignId, UUID ownerId, UUID sessionId) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO actors (id, campaign_id, owner_id, name, created_at, created_in_session_id) VALUES (?, ?, ?, 'Test Actor', now(), ?)",
        id,
        campaignId,
        ownerId,
        sessionId);
    return id;
  }
}
