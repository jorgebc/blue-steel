package com.bluesteel.adapters.out.persistence.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import com.bluesteel.TestcontainersPostgresBaseIT;
import com.bluesteel.application.model.ingestion.EntityContext;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Testcontainers integration test for {@link EntityQueryRetrievalAdapter} — verifies the native
 * cross-type pgvector Query Mode retrieval (D-062, D-063, D-034). Uses the {@code local} profile so
 * the {@code entity_embeddings} column is {@code vector(1024)} (D-088).
 */
@DisplayName("EntityQueryRetrievalAdapter (F3.2.2)")
class EntityQueryRetrievalAdapterIT extends TestcontainersPostgresBaseIT {

  // Local profile uses embeddingDimension=1024 (application-local.properties, D-088)
  private static final int EMBEDDING_DIMENSION = 1024;

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EntityQueryRetrievalAdapter sut;

  private UUID userId;
  private UUID campaignId;
  private UUID committedSessionId;

  // Query vector points at axis 0 — cosine distance 0 to an axis-0 vector, 1 to an axis-1 vector.
  private final float[] queryVector = unitVector(0);

  @BeforeEach
  void seedData() {
    userId = insertUser("query-it-" + UUID.randomUUID() + "@test.com");
    campaignId = insertCampaign(userId);
    committedSessionId = insertSession(campaignId, userId, "committed");
  }

  @Test
  @DisplayName(
      "should return committed snapshots across entity types ordered by ascending cosine distance")
  void retrieve_ordersByCosineDistanceAcrossTypes() {
    // Actor embedding is an exact match (distance 0); space embedding is orthogonal (distance 1).
    UUID actorId = insertActor(campaignId, userId, committedSessionId, "Aldric");
    UUID actorVersionId =
        insertActorVersion(actorId, committedSessionId, 1, "{\"name\":\"Aldric\"}");
    insertEmbedding(actorId, actorVersionId, committedSessionId, "actor", unitVector(0));

    UUID spaceId = insertSpace(campaignId, userId, committedSessionId, "Thornwall");
    UUID spaceVersionId =
        insertSpaceVersion(spaceId, committedSessionId, 1, "{\"name\":\"Thornwall\"}");
    insertEmbedding(spaceId, spaceVersionId, committedSessionId, "space", unitVector(1));

    List<EntityContext> results = sut.retrieveRelevantContext(campaignId, queryVector, 8);

    assertThat(results).hasSize(2);
    EntityContext first = results.get(0);
    assertThat(first.entityId()).isEqualTo(actorId);
    assertThat(first.entityType()).isEqualTo("actor");
    assertThat(first.name()).isEqualTo("Aldric");
    assertThat(first.sessionId()).isEqualTo(committedSessionId);
    assertThat(first.versionNumber()).isEqualTo(1);
    assertThat(first.stateSnapshot()).contains("Aldric");

    EntityContext second = results.get(1);
    assertThat(second.entityId()).isEqualTo(spaceId);
    assertThat(second.entityType()).isEqualTo("space");
    assertThat(second.name()).isEqualTo("Thornwall");
  }

  @Test
  @DisplayName("should exclude embeddings belonging to another campaign")
  void retrieve_scopesToCampaign() {
    UUID actorId = insertActor(campaignId, userId, committedSessionId, "Aldric");
    UUID actorVersionId =
        insertActorVersion(actorId, committedSessionId, 1, "{\"name\":\"Aldric\"}");
    insertEmbedding(actorId, actorVersionId, committedSessionId, "actor", unitVector(0));

    List<EntityContext> results = sut.retrieveRelevantContext(UUID.randomUUID(), queryVector, 8);

    assertThat(results).isEmpty();
  }

  @Test
  @DisplayName("should exclude embeddings whose session is not committed")
  void retrieve_committedSessionsOnly() {
    UUID draftSessionId = insertSession(campaignId, userId, "draft");
    UUID actorId = insertActor(campaignId, userId, draftSessionId, "Draft Actor");
    UUID actorVersionId =
        insertActorVersion(actorId, draftSessionId, 1, "{\"name\":\"Draft Actor\"}");
    insertEmbedding(actorId, actorVersionId, draftSessionId, "actor", unitVector(0));

    List<EntityContext> results = sut.retrieveRelevantContext(campaignId, queryVector, 8);

    assertThat(results).isEmpty();
  }

  @Test
  @DisplayName("should exclude committed entity versions that have no embedding row")
  void retrieve_excludesVersionsWithoutEmbedding() {
    // Actor with a version but NO entity_embeddings row — must be absent (D-063).
    UUID unembeddedActorId = insertActor(campaignId, userId, committedSessionId, "Ghost");
    insertActorVersion(unembeddedActorId, committedSessionId, 1, "{\"name\":\"Ghost\"}");

    // A second actor that IS embedded, so the result set is non-empty for a meaningful assertion.
    UUID embeddedActorId = insertActor(campaignId, userId, committedSessionId, "Aldric");
    UUID embeddedVersionId =
        insertActorVersion(embeddedActorId, committedSessionId, 1, "{\"name\":\"Aldric\"}");
    insertEmbedding(embeddedActorId, embeddedVersionId, committedSessionId, "actor", unitVector(0));

    List<EntityContext> results = sut.retrieveRelevantContext(campaignId, queryVector, 8);

    assertThat(results).hasSize(1);
    assertThat(results.get(0).entityId()).isEqualTo(embeddedActorId);
  }

  @Test
  @DisplayName("should cap the number of results at topN")
  void retrieve_respectsTopNLimit() {
    UUID actorId = insertActor(campaignId, userId, committedSessionId, "Aldric");
    UUID actorVersionId =
        insertActorVersion(actorId, committedSessionId, 1, "{\"name\":\"Aldric\"}");
    insertEmbedding(actorId, actorVersionId, committedSessionId, "actor", unitVector(0));

    UUID spaceId = insertSpace(campaignId, userId, committedSessionId, "Thornwall");
    UUID spaceVersionId =
        insertSpaceVersion(spaceId, committedSessionId, 1, "{\"name\":\"Thornwall\"}");
    insertEmbedding(spaceId, spaceVersionId, committedSessionId, "space", unitVector(1));

    List<EntityContext> results = sut.retrieveRelevantContext(campaignId, queryVector, 1);

    assertThat(results).hasSize(1);
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private static float[] unitVector(int axis) {
    float[] v = new float[EMBEDDING_DIMENSION];
    v[axis] = 1.0f;
    return v;
  }

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

  private UUID insertSession(UUID campaignId, UUID ownerId, String status) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO sessions (id, campaign_id, owner_id, status, created_at, updated_at)"
            + " VALUES (?, ?, ?, ?, now(), now())",
        id,
        campaignId,
        ownerId,
        status);
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

  private UUID insertActorVersion(
      UUID actorId, UUID sessionId, int versionNumber, String fullSnapshotJson) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO actor_versions (id, actor_id, session_id, version_number, full_snapshot, created_at)"
            + " VALUES (?, ?, ?, ?, ?::jsonb, now())",
        id,
        actorId,
        sessionId,
        versionNumber,
        fullSnapshotJson);
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

  private UUID insertSpaceVersion(
      UUID spaceId, UUID sessionId, int versionNumber, String fullSnapshotJson) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO space_versions (id, space_id, session_id, version_number, full_snapshot, created_at)"
            + " VALUES (?, ?, ?, ?, ?::jsonb, now())",
        id,
        spaceId,
        sessionId,
        versionNumber,
        fullSnapshotJson);
    return id;
  }

  private UUID insertEmbedding(
      UUID entityId, UUID entityVersionId, UUID sessionId, String entityType, float[] vector) {
    UUID id = UUID.randomUUID();
    String vectorLiteral = EntitySimilaritySearchAdapter.toVectorLiteral(vector);
    jdbcTemplate.update(
        "INSERT INTO entity_embeddings"
            + " (id, entity_type, entity_id, entity_version_id, session_id, embedding, created_at)"
            + " VALUES (?, ?, ?, ?, ?, ?::vector, now())",
        id,
        entityType,
        entityId,
        entityVersionId,
        sessionId,
        vectorLiteral);
    return id;
  }
}
