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

/** Verifies that the F2.1.3 event + relation schema changesets apply correctly (D-021, D-035). */
@DisplayName("Event and relation schema migration (F2.1.3)")
class WorldStateEventRelationSchemaIT extends TestcontainersPostgresBaseIT {

  @Autowired private JdbcTemplate jdbcTemplate;

  // -------------------------------------------------------------------------
  // Table existence
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("should create the events table")
  void eventsTableExists() {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name = 'events'",
            Long.class);
    assertThat(count).isEqualTo(1L);
  }

  @Test
  @DisplayName("should create the event_versions table")
  void eventVersionsTableExists() {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name = 'event_versions'",
            Long.class);
    assertThat(count).isEqualTo(1L);
  }

  @Test
  @DisplayName("should create the relations table")
  void relationsTableExists() {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name = 'relations'",
            Long.class);
    assertThat(count).isEqualTo(1L);
  }

  @Test
  @DisplayName("should create the relation_versions table")
  void relationVersionsTableExists() {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name = 'relation_versions'",
            Long.class);
    assertThat(count).isEqualTo(1L);
  }

  // -------------------------------------------------------------------------
  // D-021: campaign_id + owner_id on head tables
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("should have campaign_id and owner_id columns on events (D-021)")
  void eventsHasCampaignAndOwnerColumns() {
    for (String col : new String[] {"campaign_id", "owner_id"}) {
      Long count =
          jdbcTemplate.queryForObject(
              "SELECT count(*) FROM information_schema.columns WHERE table_name = 'events' AND column_name = ?",
              Long.class,
              col);
      assertThat(count).as("events.%s", col).isEqualTo(1L);
    }
  }

  @Test
  @DisplayName("should have campaign_id and owner_id columns on relations (D-021)")
  void relationsHasCampaignAndOwnerColumns() {
    for (String col : new String[] {"campaign_id", "owner_id"}) {
      Long count =
          jdbcTemplate.queryForObject(
              "SELECT count(*) FROM information_schema.columns WHERE table_name = 'relations' AND column_name = ?",
              Long.class,
              col);
      assertThat(count).as("relations.%s", col).isEqualTo(1L);
    }
  }

  // -------------------------------------------------------------------------
  // D-035: version FK enforcement
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("should enforce event_versions.event_id FK — orphan version must fail")
  void eventVersionsEventFkEnforced() {
    UUID userId = insertUser("event-fk@test.com");
    UUID campaignId = insertCampaign(userId);
    UUID sessionId = insertSession(campaignId, userId);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO event_versions
                      (id, event_id, session_id, version_number, full_snapshot, created_at)
                    VALUES (?, ?, ?, 1, '{}', now())
                    """,
                    UUID.randomUUID(),
                    UUID.randomUUID(), // non-existent event
                    sessionId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("should enforce relation_versions.relation_id FK — orphan version must fail")
  void relationVersionsRelationFkEnforced() {
    UUID userId = insertUser("relation-fk@test.com");
    UUID campaignId = insertCampaign(userId);
    UUID sessionId = insertSession(campaignId, userId);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO relation_versions
                      (id, relation_id, session_id, version_number, full_snapshot, created_at)
                    VALUES (?, ?, ?, 1, '{}', now())
                    """,
                    UUID.randomUUID(),
                    UUID.randomUUID(), // non-existent relation
                    sessionId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("should persist event version when event and session exist (D-035)")
  void eventVersionPersistsWhenRefsExist() {
    UUID userId = insertUser("event-version-ok@test.com");
    UUID campaignId = insertCampaign(userId);
    UUID sessionId = insertSession(campaignId, userId);
    UUID eventId = insertEvent(campaignId, userId, sessionId);

    jdbcTemplate.update(
        """
        INSERT INTO event_versions
          (id, event_id, session_id, version_number, full_snapshot, created_at)
        VALUES (?, ?, ?, 1, '{"name":"Battle of the Keep"}', now())
        """,
        UUID.randomUUID(),
        eventId,
        sessionId);

    Long count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM event_versions WHERE event_id = ?", Long.class, eventId);
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

  private UUID insertEvent(UUID campaignId, UUID ownerId, UUID sessionId) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO events (id, campaign_id, owner_id, name, created_at, created_in_session_id) VALUES (?, ?, ?, 'Test Event', now(), ?)",
        id,
        campaignId,
        ownerId,
        sessionId);
    return id;
  }
}
