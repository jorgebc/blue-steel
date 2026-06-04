package com.bluesteel.adapters.out.persistence.worldstate;

import static org.assertj.core.api.Assertions.assertThat;

import com.bluesteel.TestcontainersPostgresBaseIT;
import com.bluesteel.application.model.worldstate.RelationDetailView;
import com.bluesteel.application.model.worldstate.RelationSummaryView;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Testcontainers integration test for {@link RelationReadAdapter} — verifies campaign scoping, the
 * actor endpoint filter on either side, exposure of resolved/unresolved endpoints, and full ordered
 * version history in detail (F4.3.5).
 */
@DisplayName("RelationReadAdapter (F4.3.5)")
class RelationReadAdapterIT extends TestcontainersPostgresBaseIT {

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private RelationReadAdapter sut;

  private UUID userId;
  private UUID campaignId;
  private UUID session1Id;
  private UUID session2Id;
  private UUID miraId;
  private UUID thornwickId;
  private UUID aldricId;
  private UUID guidesRelationId;
  private UUID unresolvedRelationId;

  @BeforeEach
  void seedData() {
    userId = insertUser("relit-" + UUID.randomUUID() + "@test.com");
    campaignId = insertCampaign(userId);
    session1Id = insertSession(campaignId, userId, 1);
    session2Id = insertSession(campaignId, userId, 2);

    miraId = insertActor(campaignId, userId, session1Id, "Mira");
    aldricId = insertActor(campaignId, userId, session1Id, "Aldric");
    thornwickId = insertSpace(campaignId, userId, session1Id, "Thornwick");

    // Resolved relation Mira -> Thornwick, two versions (latest carries kind=alliance)
    guidesRelationId =
        insertRelation(
            campaignId,
            userId,
            session1Id,
            "Mira guides the party",
            miraId,
            "actor",
            thornwickId,
            "space");
    insertRelationVersion(
        guidesRelationId, session1Id, 1, null, "{\"name\":\"Mira guides the party\"}");
    insertRelationVersion(
        guidesRelationId,
        session2Id,
        2,
        "{\"kind\":\"alliance\"}",
        "{\"name\":\"Mira guides the party\",\"kind\":\"alliance\"}");

    // Unresolved relation (no endpoints), single version
    unresolvedRelationId =
        insertRelation(campaignId, userId, session1Id, "A whispered rumor", null, null, null, null);
    insertRelationVersion(
        unresolvedRelationId, session1Id, 1, null, "{\"name\":\"A whispered rumor\"}");
  }

  @Test
  @DisplayName("should list campaign relations with resolved endpoints and latest-version kind")
  void list_returnsRelationsWithEndpointsAndKind() {
    List<RelationSummaryView> relations = sut.list(campaignId, null);

    assertThat(relations).hasSize(2);
    RelationSummaryView guides =
        relations.stream()
            .filter(r -> r.relationId().equals(guidesRelationId))
            .findFirst()
            .orElseThrow();
    assertThat(guides.name()).isEqualTo("Mira guides the party");
    assertThat(guides.kind()).isEqualTo("alliance");
    assertThat(guides.sourceEntityId()).isEqualTo(miraId);
    assertThat(guides.sourceEntityType()).isEqualTo("actor");
    assertThat(guides.targetEntityId()).isEqualTo(thornwickId);
    assertThat(guides.targetEntityType()).isEqualTo("space");
    assertThat(guides.sessionId()).isEqualTo(session1Id);
  }

  @Test
  @DisplayName("should surface null endpoints for an unresolved relation")
  void list_unresolvedRelation_hasNullEndpoints() {
    RelationSummaryView unresolved =
        sut.list(campaignId, null).stream()
            .filter(r -> r.relationId().equals(unresolvedRelationId))
            .findFirst()
            .orElseThrow();

    assertThat(unresolved.sourceEntityId()).isNull();
    assertThat(unresolved.targetEntityId()).isNull();
    assertThat(unresolved.kind()).isNull();
  }

  @Test
  @DisplayName("should filter relations by an actor on the source endpoint")
  void list_actorFilter_matchesSourceEndpoint() {
    List<RelationSummaryView> relations = sut.list(campaignId, miraId);

    assertThat(relations).hasSize(1);
    assertThat(relations.get(0).relationId()).isEqualTo(guidesRelationId);
  }

  @Test
  @DisplayName("should filter relations by an entity on the target endpoint")
  void list_actorFilter_matchesTargetEndpoint() {
    List<RelationSummaryView> relations = sut.list(campaignId, thornwickId);

    assertThat(relations).hasSize(1);
    assertThat(relations.get(0).relationId()).isEqualTo(guidesRelationId);
  }

  @Test
  @DisplayName("should return no relations when the actor filter matches neither endpoint")
  void list_actorFilter_noMatch_returnsEmpty() {
    assertThat(sut.list(campaignId, aldricId)).isEmpty();
  }

  @Test
  @DisplayName("should return the relation detail with endpoints and full ordered history")
  void getWithHistory_returnsEndpointsAndOrderedVersions() {
    RelationDetailView detail = sut.getWithHistory(campaignId, guidesRelationId);

    assertThat(detail).isNotNull();
    assertThat(detail.relationId()).isEqualTo(guidesRelationId);
    assertThat(detail.name()).isEqualTo("Mira guides the party");
    assertThat(detail.kind()).isEqualTo("alliance");
    assertThat(detail.sourceEntityId()).isEqualTo(miraId);
    assertThat(detail.targetEntityId()).isEqualTo(thornwickId);
    assertThat(detail.ownerId()).isEqualTo(userId);
    assertThat(detail.versions()).hasSize(2);
    assertThat(detail.versions().get(0).versionNumber()).isEqualTo(1);
    assertThat(detail.versions().get(1).versionNumber()).isEqualTo(2);
    assertThat(detail.versions().get(1).sessionSequenceNumber()).isEqualTo(2);
  }

  @Test
  @DisplayName("should scope list and detail to the requested campaign")
  void list_and_detail_scopedToCampaign() {
    UUID otherCampaignId = insertCampaign(userId);

    assertThat(sut.list(otherCampaignId, null)).isEmpty();
    assertThat(sut.getWithHistory(otherCampaignId, guidesRelationId)).isNull();
  }

  @Test
  @DisplayName("should return null detail when the relation does not exist")
  void getWithHistory_unknownRelation_returnsNull() {
    assertThat(sut.getWithHistory(campaignId, UUID.randomUUID())).isNull();
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
        "INSERT INTO campaigns (id, name, created_by, created_at)"
            + " VALUES (?, 'Test Campaign', ?, now())",
        id,
        createdBy);
    return id;
  }

  private UUID insertSession(UUID campaignId, UUID ownerId, int sequenceNumber) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO sessions (id, campaign_id, owner_id, status, sequence_number, created_at,"
            + " updated_at) VALUES (?, ?, ?, 'committed', ?, now(), now())",
        id,
        campaignId,
        ownerId,
        sequenceNumber);
    return id;
  }

  private UUID insertActor(UUID campaignId, UUID ownerId, UUID sessionId, String name) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO actors (id, campaign_id, owner_id, name, created_at, created_in_session_id)"
            + " VALUES (?, ?, ?, ?, now(), ?)",
        id,
        campaignId,
        ownerId,
        name,
        sessionId);
    return id;
  }

  private UUID insertSpace(UUID campaignId, UUID ownerId, UUID sessionId, String name) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO spaces (id, campaign_id, owner_id, name, created_at, created_in_session_id)"
            + " VALUES (?, ?, ?, ?, now(), ?)",
        id,
        campaignId,
        ownerId,
        name,
        sessionId);
    return id;
  }

  private UUID insertRelation(
      UUID campaignId,
      UUID ownerId,
      UUID sessionId,
      String name,
      UUID sourceId,
      String sourceType,
      UUID targetId,
      String targetType) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO relations (id, campaign_id, owner_id, name, created_at, created_in_session_id,"
            + " source_entity_id, source_entity_type, target_entity_id, target_entity_type)"
            + " VALUES (?, ?, ?, ?, now(), ?, ?, ?, ?, ?)",
        id,
        campaignId,
        ownerId,
        name,
        sessionId,
        sourceId,
        sourceType,
        targetId,
        targetType);
    return id;
  }

  private void insertRelationVersion(
      UUID relationId,
      UUID sessionId,
      int versionNumber,
      String changedFieldsJson,
      String snapshot) {
    jdbcTemplate.update(
        "INSERT INTO relation_versions (id, relation_id, session_id, version_number, changed_fields,"
            + " full_snapshot, created_at) VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, now())",
        UUID.randomUUID(),
        relationId,
        sessionId,
        versionNumber,
        changedFieldsJson,
        snapshot);
  }
}
