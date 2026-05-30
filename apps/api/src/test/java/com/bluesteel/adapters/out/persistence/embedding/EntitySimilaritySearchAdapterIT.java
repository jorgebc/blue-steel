package com.bluesteel.adapters.out.persistence.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import com.bluesteel.TestcontainersPostgresBaseIT;
import com.bluesteel.application.model.ingestion.SimilarityResult;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Testcontainers integration test for {@link EntitySimilaritySearchAdapter} — verifies the native
 * pgvector similarity query (D-041, D-062). Uses the {@code local} profile so the {@code
 * entity_embeddings} column is {@code vector(1024)} (D-088).
 */
@DisplayName("EntitySimilaritySearchAdapter (F2.5.2)")
class EntitySimilaritySearchAdapterIT extends TestcontainersPostgresBaseIT {

  // Local profile uses embeddingDimension=1024 (application-local.properties, D-088)
  private static final int EMBEDDING_DIMENSION = 1024;

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EntitySimilaritySearchAdapter sut;

  private UUID userId;
  private UUID campaignId;
  private UUID sessionId;
  private UUID actorId;
  private UUID actorVersionId;
  private UUID embeddingId;

  // Query vector with index 0 = 1.0f — cosine similarity with the seeded vector is 1.0
  private final float[] queryVector = buildTestVector();

  @BeforeEach
  void seedData() {
    userId = insertUser("search-it-" + UUID.randomUUID() + "@test.com");
    campaignId = insertCampaign(userId);
    sessionId = insertSession(campaignId, userId);
    actorId = insertActor(campaignId, userId, sessionId, "Aldric");
    actorVersionId =
        insertActorVersion(actorId, sessionId, 1, "{\"name\":\"Aldric\",\"role\":\"warrior\"}");
    embeddingId = insertEmbedding(actorId, actorVersionId, sessionId, "actor", buildTestVector());
  }

  @Test
  @DisplayName(
      "should return a matching SimilarityResult with positive similarity for an exact-match vector")
  void search_returnsCandidateWithSimilarity() {
    List<SimilarityResult> results = sut.search(queryVector, campaignId, "actor", 5);

    assertThat(results).hasSize(1);
    SimilarityResult result = results.get(0);
    assertThat(result.entityId()).isEqualTo(actorId);
    assertThat(result.entityType()).isEqualTo("actor");
    assertThat(result.name()).isEqualTo("Aldric");
    assertThat(result.sessionId()).isEqualTo(sessionId);
    assertThat(result.versionNumber()).isEqualTo(1);
    assertThat(result.similarity()).isGreaterThan(0.0);
    assertThat(result.stateSnapshot()).contains("Aldric");
  }

  @Test
  @DisplayName("should return empty list when campaign_id does not match")
  void search_wrongCampaignId_returnsEmpty() {
    List<SimilarityResult> results = sut.search(queryVector, UUID.randomUUID(), "actor", 5);

    assertThat(results).isEmpty();
  }

  @Test
  @DisplayName("should return empty list when entity_type does not match")
  void search_wrongEntityType_returnsEmpty() {
    List<SimilarityResult> results = sut.search(queryVector, campaignId, "space", 5);

    assertThat(results).isEmpty();
  }

  @Test
  @DisplayName("should return empty list when topN is zero")
  void search_topNZero_returnsEmpty() {
    List<SimilarityResult> results = sut.search(queryVector, campaignId, "actor", 0);

    assertThat(results).isEmpty();
  }

  @Test
  @DisplayName("should scope results to topN when multiple embeddings exist")
  void search_respectsTopNLimit() {
    // Seed a second actor + embedding in the same campaign
    UUID actor2Id = insertActor(campaignId, userId, sessionId, "Seraphine");
    UUID actor2VersionId = insertActorVersion(actor2Id, sessionId, 1, "{\"name\":\"Seraphine\"}");
    insertEmbedding(actor2Id, actor2VersionId, sessionId, "actor", buildTestVector());

    List<SimilarityResult> results = sut.search(queryVector, campaignId, "actor", 1);

    assertThat(results).hasSize(1);
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private static float[] buildTestVector() {
    float[] v = new float[EMBEDDING_DIMENSION];
    v[0] = 1.0f;
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

  private UUID insertSession(UUID campaignId, UUID ownerId) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO sessions (id, campaign_id, owner_id, status, created_at, updated_at)"
            + " VALUES (?, ?, ?, 'committed', now(), now())",
        id,
        campaignId,
        ownerId);
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
