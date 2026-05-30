package com.bluesteel.adapters.out.persistence.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import com.bluesteel.TestcontainersPostgresBaseIT;
import com.bluesteel.adapters.out.persistence.campaign.CampaignPersistenceAdapter;
import com.bluesteel.adapters.out.persistence.session.SessionPersistenceAdapter;
import com.bluesteel.adapters.out.persistence.user.UserPersistenceAdapter;
import com.bluesteel.adapters.out.persistence.worldstate.WorldStateAdapter;
import com.bluesteel.application.model.embedding.EntityEmbeddingRow;
import com.bluesteel.application.model.worldstate.CommittedEntityVersion;
import com.bluesteel.application.model.worldstate.EntityWriteCommand;
import com.bluesteel.domain.campaign.Campaign;
import com.bluesteel.domain.session.Session;
import com.bluesteel.domain.user.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@DisplayName("EntityEmbeddingWriteAdapter")
class EntityEmbeddingWriteAdapterIT extends TestcontainersPostgresBaseIT {

  @Autowired private EntityEmbeddingWriteAdapter adapter;
  @Autowired private WorldStateAdapter worldStateAdapter;
  @Autowired private UserPersistenceAdapter userAdapter;
  @Autowired private CampaignPersistenceAdapter campaignAdapter;
  @Autowired private SessionPersistenceAdapter sessionAdapter;
  @Autowired private JdbcTemplate jdbcTemplate;

  private UUID campaignId;
  private UUID ownerId;
  private UUID sessionId;
  private CommittedEntityVersion entityVersion;

  @BeforeEach
  void setUp() {
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

    UUID userId = UUID.randomUUID();
    User user = User.create(userId, userId + "@example.com", "$2a$10$hash", false, false, now);
    userAdapter.save(user);
    ownerId = userId;

    UUID cId = UUID.randomUUID();
    Campaign campaign = Campaign.create(cId, "Campaign-" + cId, userId, now);
    campaignAdapter.save(campaign);
    campaignId = cId;

    UUID sId = UUID.randomUUID();
    Session session = Session.create(sId, cId, userId, now);
    session.startProcessing();
    session.toDraft("{}");
    session.commit(1);
    sessionAdapter.save(session);
    sessionId = sId;

    entityVersion =
        worldStateAdapter.writeEntity(
            new EntityWriteCommand(
                "actor", null, cId, userId, "Frodo", null, Map.of("name", "Frodo"), sId));
  }

  @Test
  @DisplayName("should insert an embedding row for an entity version")
  void insert_newRow_persistsEmbedding() {
    // Dimension must match the test DB column (local profile: embeddingDimension=1024)
    float[] embedding = new float[1024];
    embedding[0] = 0.1f;
    embedding[1] = 0.2f;
    embedding[2] = 0.3f;
    EntityEmbeddingRow row =
        new EntityEmbeddingRow(
            entityVersion.entityType(),
            entityVersion.entityId(),
            entityVersion.entityVersionId(),
            sessionId,
            embedding,
            entityVersion.contentHash());

    adapter.insert(row);

    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            "SELECT entity_type, entity_id, entity_version_id, content_hash"
                + " FROM entity_embeddings WHERE entity_version_id = ?",
            entityVersion.entityVersionId());
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).get("entity_type")).isEqualTo("actor");
    assertThat(rows.get(0).get("entity_version_id")).isEqualTo(entityVersion.entityVersionId());
    assertThat(rows.get(0).get("content_hash")).isEqualTo(entityVersion.contentHash());
  }
}
