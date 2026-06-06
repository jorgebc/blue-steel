package com.bluesteel.adapters.out.persistence.worldstate;

import static org.assertj.core.api.Assertions.assertThat;

import com.bluesteel.TestcontainersPostgresBaseIT;
import com.bluesteel.application.model.worldstate.EntityLinks;
import com.bluesteel.application.model.worldstate.EntitySummaryView;
import com.bluesteel.application.model.worldstate.TimelineEntryView;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Testcontainers integration test for {@link EntityLinksReadAdapter} (F4.7.1) — verifies that an
 * actor's and a space's profile cross-links are assembled from the structured links written at
 * commit: relations on either endpoint, resolved related entities (unresolved endpoints skipped),
 * events via actor involvement / space location, distinct appearance sessions, and campaign
 * scoping.
 */
@DisplayName("EntityLinksReadAdapter (F4.7.1)")
class EntityLinksReadAdapterIT extends TestcontainersPostgresBaseIT {

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EntityLinksReadAdapter sut;

  private UUID userId;
  private UUID campaignId;
  private UUID session1Id;
  private UUID session2Id;
  private UUID miraId;
  private UUID aldricId;
  private UUID thornwickId;
  private UUID battleEventId;
  private UUID duelEventId;

  @BeforeEach
  void seedData() {
    userId = insertUser("linksit-" + UUID.randomUUID() + "@test.com");
    campaignId = insertCampaign(userId);
    session1Id = insertSession(campaignId, userId, 1);
    session2Id = insertSession(campaignId, userId, 2);

    // Actors — Mira appears once (s1), Aldric appears in s1 and s2 (two versions).
    miraId = insertActor(campaignId, userId, session1Id, "Mira");
    insertActorVersion(miraId, session1Id, 1, "{\"name\":\"Mira\"}");
    aldricId = insertActor(campaignId, userId, session1Id, "Aldric");
    insertActorVersion(aldricId, session1Id, 1, "{\"name\":\"Aldric\"}");
    insertActorVersion(aldricId, session2Id, 2, "{\"name\":\"Aldric\"}");

    // Space — Thornwick, one version.
    thornwickId = insertSpace(campaignId, userId, session1Id, "Thornwick");
    insertSpaceVersion(thornwickId, session1Id, 1, "{\"name\":\"Thornwick\"}");

    // Relations touching Mira: guides (Mira->Thornwick), trusts (Aldric->Mira),
    // and an unresolved rumor (Mira->null) whose opposite must be skipped.
    UUID guidesId =
        insertRelation(
            campaignId, userId, session1Id, "Mira guides", miraId, "actor", thornwickId, "space");
    insertRelationVersion(
        guidesId, session1Id, 1, "{\"name\":\"Mira guides\",\"kind\":\"alliance\"}");
    UUID trustsId =
        insertRelation(
            campaignId,
            userId,
            session1Id,
            "Aldric trusts Mira",
            aldricId,
            "actor",
            miraId,
            "actor");
    insertRelationVersion(trustsId, session1Id, 1, "{\"name\":\"Aldric trusts Mira\"}");
    UUID rumorId =
        insertRelation(
            campaignId, userId, session1Id, "A rumor about Mira", miraId, "actor", null, null);
    insertRelationVersion(rumorId, session1Id, 1, "{\"name\":\"A rumor about Mira\"}");

    // Events: a battle in Thornwick involving Mira (s1); a duel with no space involving Aldric
    // (s2).
    battleEventId =
        insertEvent(campaignId, userId, session1Id, "The Battle", thornwickId, "battle");
    insertEventVersion(battleEventId, session1Id, 1, "{\"name\":\"The Battle\"}");
    insertEventInvolvedActor(battleEventId, miraId);
    duelEventId = insertEvent(campaignId, userId, session2Id, "The Duel", null, "duel");
    insertEventVersion(duelEventId, session2Id, 1, "{\"name\":\"The Duel\"}");
    insertEventInvolvedActor(duelEventId, aldricId);
  }

  @Test
  @DisplayName("should return an actor's relations on either endpoint and resolve related entities")
  void getLinks_actor_returnsRelationsAndRelatedEntities() {
    EntityLinks links = sut.getLinks("actor", campaignId, miraId);

    // guides (source), trusts (target), rumor (source) all reference Mira.
    assertThat(links.relations()).hasSize(3);
    // Thornwick (from guides) and Aldric (from trusts) resolve; rumor's null opposite is skipped.
    assertThat(links.relatedEntities()).hasSize(2);
    assertThat(links.relatedEntities().stream().map(EntitySummaryView::name))
        .containsExactlyInAnyOrder("Thornwick", "Aldric");
  }

  @Test
  @DisplayName("should return the events an actor is involved in")
  void getLinks_actor_returnsInvolvedEvents() {
    EntityLinks links = sut.getLinks("actor", campaignId, miraId);

    assertThat(links.events()).hasSize(1);
    TimelineEntryView battle = links.events().get(0);
    assertThat(battle.eventId()).isEqualTo(battleEventId);
    assertThat(battle.eventType()).isEqualTo("battle");
    assertThat(battle.spaceName()).isEqualTo("Thornwick");
    assertThat(battle.involvedActorNames()).contains("Mira");
  }

  @Test
  @DisplayName("should return a space's relations, related entities and events occurring there")
  void getLinks_space_returnsRelationsAndLocatedEvents() {
    EntityLinks links = sut.getLinks("space", campaignId, thornwickId);

    // Only the guides relation has Thornwick as an endpoint.
    assertThat(links.relations()).hasSize(1);
    assertThat(links.relatedEntities().stream().map(EntitySummaryView::name))
        .containsExactly("Mira");
    assertThat(links.events()).hasSize(1);
    assertThat(links.events().get(0).eventId()).isEqualTo(battleEventId);
  }

  @Test
  @DisplayName("should return distinct appearance session ids ordered by first appearance")
  void getLinks_actor_returnsDistinctAppearanceSessions() {
    EntityLinks links = sut.getLinks("actor", campaignId, aldricId);

    assertThat(links.appearanceSessionIds()).containsExactly(session1Id, session2Id);
    assertThat(links.events()).hasSize(1);
    assertThat(links.events().get(0).eventId()).isEqualTo(duelEventId);
  }

  @Test
  @DisplayName("should scope all link sections to the requested campaign")
  void getLinks_scopedToCampaign() {
    UUID otherCampaignId = insertCampaign(userId);

    EntityLinks links = sut.getLinks("actor", otherCampaignId, miraId);

    assertThat(links.relations()).isEmpty();
    assertThat(links.relatedEntities()).isEmpty();
    assertThat(links.events()).isEmpty();
    // The entity's own version rows are not campaign-filtered, but a foreign campaign has none
    // here.
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

  private void insertActorVersion(
      UUID actorId, UUID sessionId, int versionNumber, String snapshot) {
    jdbcTemplate.update(
        "INSERT INTO actor_versions (id, actor_id, session_id, version_number, full_snapshot,"
            + " created_at) VALUES (?, ?, ?, ?, ?::jsonb, now())",
        UUID.randomUUID(),
        actorId,
        sessionId,
        versionNumber,
        snapshot);
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

  private void insertSpaceVersion(
      UUID spaceId, UUID sessionId, int versionNumber, String snapshot) {
    jdbcTemplate.update(
        "INSERT INTO space_versions (id, space_id, session_id, version_number, full_snapshot,"
            + " created_at) VALUES (?, ?, ?, ?, ?::jsonb, now())",
        UUID.randomUUID(),
        spaceId,
        sessionId,
        versionNumber,
        snapshot);
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
      UUID relationId, UUID sessionId, int versionNumber, String snapshot) {
    jdbcTemplate.update(
        "INSERT INTO relation_versions (id, relation_id, session_id, version_number, full_snapshot,"
            + " created_at) VALUES (?, ?, ?, ?, ?::jsonb, now())",
        UUID.randomUUID(),
        relationId,
        sessionId,
        versionNumber,
        snapshot);
  }

  private UUID insertEvent(
      UUID campaignId, UUID ownerId, UUID sessionId, String name, UUID spaceId, String eventType) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO events (id, campaign_id, owner_id, name, created_at, created_in_session_id,"
            + " space_id, event_type) VALUES (?, ?, ?, ?, now(), ?, ?, ?)",
        id,
        campaignId,
        ownerId,
        name,
        sessionId,
        spaceId,
        eventType);
    return id;
  }

  private void insertEventVersion(
      UUID eventId, UUID sessionId, int versionNumber, String snapshot) {
    jdbcTemplate.update(
        "INSERT INTO event_versions (id, event_id, session_id, version_number, full_snapshot,"
            + " created_at) VALUES (?, ?, ?, ?, ?::jsonb, now())",
        UUID.randomUUID(),
        eventId,
        sessionId,
        versionNumber,
        snapshot);
  }

  private void insertEventInvolvedActor(UUID eventId, UUID actorId) {
    jdbcTemplate.update(
        "INSERT INTO event_involved_actors (event_id, actor_id) VALUES (?, ?)", eventId, actorId);
  }
}
