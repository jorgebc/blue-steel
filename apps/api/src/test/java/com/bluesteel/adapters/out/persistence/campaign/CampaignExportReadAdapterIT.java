package com.bluesteel.adapters.out.persistence.campaign;

import static org.assertj.core.api.Assertions.assertThat;

import com.bluesteel.TestcontainersPostgresBaseIT;
import com.bluesteel.application.model.campaign.ArchivedAnnotation;
import com.bluesteel.application.model.campaign.ArchivedEntity;
import com.bluesteel.application.model.campaign.ArchivedEntityVersion;
import com.bluesteel.application.model.campaign.ArchivedSession;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Testcontainers integration test for {@link CampaignExportReadAdapter} — verifies the bulk export
 * reads (all four entity types with full ordered version history, annotations, sessions) and the
 * cheap {@code countEntities} precheck against a real Postgres instance (F7.1.2).
 */
@DisplayName("CampaignExportReadAdapter (F7.1.2)")
class CampaignExportReadAdapterIT extends TestcontainersPostgresBaseIT {

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private CampaignExportReadAdapter sut;

  private UUID userId;
  private UUID campaignId;
  private UUID otherCampaignId;
  private UUID session1Id;
  private UUID session2Id;
  private UUID aldricId;
  private UUID tavernId;
  private UUID battleId;
  private UUID alliesId;

  @BeforeEach
  void seedData() {
    userId = insertUser("exportit-" + UUID.randomUUID() + "@test.com");
    campaignId = insertCampaign(userId);
    otherCampaignId = insertCampaign(userId);
    session1Id = insertSession(campaignId, userId, 1, "committed");
    session2Id = insertSession(campaignId, userId, 2, "committed");

    aldricId = insertEntity("actors", campaignId, userId, session1Id, "Aldric");
    insertVersion(
        "actor_versions",
        "actor_id",
        aldricId,
        session1Id,
        1,
        null,
        "{\"name\":\"Aldric\",\"role\":\"squire\"}");
    insertVersion(
        "actor_versions",
        "actor_id",
        aldricId,
        session2Id,
        2,
        "{\"role\":\"knight\"}",
        "{\"name\":\"Aldric\",\"role\":\"knight\"}");

    tavernId = insertEntity("spaces", campaignId, userId, session1Id, "The Prancing Pony");
    insertVersion(
        "space_versions",
        "space_id",
        tavernId,
        session1Id,
        1,
        null,
        "{\"name\":\"The Prancing Pony\"}");

    battleId = insertEntity("events", campaignId, userId, session2Id, "The Ambush");
    insertVersion(
        "event_versions", "event_id", battleId, session2Id, 1, null, "{\"name\":\"The Ambush\"}");

    alliesId = insertEntity("relations", campaignId, userId, session2Id, "Aldric-Tavern bond");
    insertVersion(
        "relation_versions",
        "relation_id",
        alliesId,
        session2Id,
        1,
        null,
        "{\"kind\":\"frequents\"}");

    insertAnnotation(campaignId, "actor", aldricId, userId, "A loyal squire");

    // Noise in another campaign — must never appear in the export.
    UUID otherActor =
        insertEntity(
            "actors",
            otherCampaignId,
            userId,
            insertSession(otherCampaignId, userId, 1, "committed"),
            "Outsider");
    insertVersion(
        "actor_versions", "actor_id", otherActor, session1Id, 1, null, "{\"name\":\"Outsider\"}");
  }

  @Test
  @DisplayName(
      "should count only the requested campaign's world-state entities across all four types")
  void countEntities_sumsAllTypesScopedToCampaign() {
    assertThat(sut.countEntities(campaignId)).isEqualTo(4L);
    assertThat(sut.countEntities(otherCampaignId)).isEqualTo(1L);
    assertThat(sut.countEntities(UUID.randomUUID())).isZero();
  }

  @Test
  @DisplayName(
      "should read all four entity types with full ordered version history and parsed snapshots")
  void readEntities_returnsAllTypesWithOrderedVersions() {
    List<ArchivedEntity> entities = sut.readEntities(campaignId);

    assertThat(entities)
        .extracting(ArchivedEntity::type)
        .containsExactly("actor", "space", "event", "relation");

    ArchivedEntity actor = entities.get(0);
    assertThat(actor.id()).isEqualTo(aldricId);
    assertThat(actor.name()).isEqualTo("Aldric");
    assertThat(actor.ownerId()).isEqualTo(userId);
    assertThat(actor.versions()).hasSize(2);

    ArchivedEntityVersion v1 = actor.versions().get(0);
    assertThat(v1.versionNumber()).isEqualTo(1);
    assertThat(v1.sessionId()).isEqualTo(session1Id);
    assertThat(v1.changedFields()).isEmpty();
    assertThat(v1.fullSnapshot()).containsEntry("role", "squire");

    ArchivedEntityVersion v2 = actor.versions().get(1);
    assertThat(v2.versionNumber()).isEqualTo(2);
    assertThat(v2.changedFields()).containsEntry("role", "knight");
    assertThat(v2.fullSnapshot()).containsEntry("role", "knight");

    assertThat(entities.get(1).id()).isEqualTo(tavernId);
    assertThat(entities.get(2).id()).isEqualTo(battleId);
    assertThat(entities.get(3).id()).isEqualTo(alliesId);
    assertThat(entities.get(3).versions().get(0).fullSnapshot()).containsEntry("kind", "frequents");
  }

  @Test
  @DisplayName("should read the campaign's annotations oldest first")
  void readAnnotations_returnsCampaignAnnotations() {
    List<ArchivedAnnotation> annotations = sut.readAnnotations(campaignId);

    assertThat(annotations).hasSize(1);
    ArchivedAnnotation annotation = annotations.get(0);
    assertThat(annotation.entityType()).isEqualTo("actor");
    assertThat(annotation.entityId()).isEqualTo(aldricId);
    assertThat(annotation.authorId()).isEqualTo(userId);
    assertThat(annotation.content()).isEqualTo("A loyal squire");
  }

  @Test
  @DisplayName("should read the campaign's sessions with their metadata")
  void readSessions_returnsCampaignSessions() {
    List<ArchivedSession> sessions = sut.readSessions(campaignId);

    assertThat(sessions)
        .extracting(ArchivedSession::id)
        .containsExactlyInAnyOrder(session1Id, session2Id);
    assertThat(sessions)
        .allSatisfy(
            s -> {
              assertThat(s.ownerId()).isEqualTo(userId);
              assertThat(s.status()).isEqualTo("committed");
              assertThat(s.sequenceNumber()).isNotNull();
            });
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private UUID insertUser(String email) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO users (id, email, password_hash, is_admin, force_password_change, created_at)"
            + " VALUES (?, ?, 'hash', FALSE, FALSE, now())",
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

  private UUID insertSession(UUID campaignId, UUID ownerId, int sequenceNumber, String status) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO sessions (id, campaign_id, owner_id, status, sequence_number, created_at,"
            + " updated_at) VALUES (?, ?, ?, ?, ?, now(), now())",
        id,
        campaignId,
        ownerId,
        status,
        sequenceNumber);
    return id;
  }

  private UUID insertEntity(
      String headTable, UUID campaignId, UUID ownerId, UUID sessionId, String name) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO "
            + headTable
            + " (id, campaign_id, owner_id, name, created_at, created_in_session_id)"
            + " VALUES (?, ?, ?, ?, now(), ?)",
        id,
        campaignId,
        ownerId,
        name,
        sessionId);
    return id;
  }

  private void insertVersion(
      String versionTable,
      String fkColumn,
      UUID entityId,
      UUID sessionId,
      int versionNumber,
      String changedFieldsJson,
      String snapshot) {
    jdbcTemplate.update(
        "INSERT INTO "
            + versionTable
            + " (id, "
            + fkColumn
            + ", session_id, version_number, changed_fields, full_snapshot, created_at)"
            + " VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, now())",
        UUID.randomUUID(),
        entityId,
        sessionId,
        versionNumber,
        changedFieldsJson,
        snapshot);
  }

  private void insertAnnotation(
      UUID campaignId, String entityType, UUID entityId, UUID authorId, String content) {
    jdbcTemplate.update(
        "INSERT INTO annotations (id, campaign_id, entity_type, entity_id, author_id, content,"
            + " created_at) VALUES (?, ?, ?, ?, ?, ?, now())",
        UUID.randomUUID(),
        campaignId,
        entityType,
        entityId,
        authorId,
        content);
  }
}
