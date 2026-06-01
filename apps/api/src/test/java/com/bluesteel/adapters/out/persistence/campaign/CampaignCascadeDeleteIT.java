package com.bluesteel.adapters.out.persistence.campaign;

import static org.assertj.core.api.Assertions.assertThat;

import com.bluesteel.TestcontainersPostgresBaseIT;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves the full campaign-delete cascade chain (migrations 0020–0022) against a real database:
 * seeding one row in every table that hangs off a campaign (directly or transitively through
 * sessions / world-state entities / proposals) and then deleting the campaign must leave no
 * orphaned rows in any of them.
 */
@DisplayName("Campaign delete cascade (0020–0022)")
class CampaignCascadeDeleteIT extends TestcontainersPostgresBaseIT {

  @Autowired private CampaignPersistenceAdapter adapter;
  @Autowired private JdbcTemplate jdbcTemplate;

  /**
   * Every table that must be emptied when its campaign is deleted, with the column to filter on.
   */
  private record ChildTable(String table, String column, UUID value) {}

  @Test
  @DisplayName("should remove all related rows across every table when a campaign is deleted")
  void deleteCampaign_cascadesToAllRelatedTables() {
    UUID userId = insertUser();
    UUID campaignId = insertCampaign(userId);
    UUID sessionId = insertSession(campaignId, userId);
    insertNarrativeBlock(sessionId);

    UUID actorId = insertEntity("actors", campaignId, userId, sessionId);
    insertVersion("actor_versions", "actor_id", actorId, sessionId);
    UUID spaceId = insertEntity("spaces", campaignId, userId, sessionId);
    insertVersion("space_versions", "space_id", spaceId, sessionId);
    UUID eventId = insertEntity("events", campaignId, userId, sessionId);
    insertVersion("event_versions", "event_id", eventId, sessionId);
    UUID relationId = insertEntity("relations", campaignId, userId, sessionId);
    insertVersion("relation_versions", "relation_id", relationId, sessionId);

    insertEmbedding(actorId, sessionId);
    insertAnnotation(campaignId, actorId, userId);
    UUID proposalId = insertProposal(campaignId, userId);
    insertProposalVote(proposalId, userId);

    ChildTable[] children = {
      new ChildTable("campaign_members", "campaign_id", campaignId),
      new ChildTable("sessions", "campaign_id", campaignId),
      new ChildTable("narrative_blocks", "session_id", sessionId),
      new ChildTable("actors", "campaign_id", campaignId),
      new ChildTable("actor_versions", "actor_id", actorId),
      new ChildTable("spaces", "campaign_id", campaignId),
      new ChildTable("space_versions", "space_id", spaceId),
      new ChildTable("events", "campaign_id", campaignId),
      new ChildTable("event_versions", "event_id", eventId),
      new ChildTable("relations", "campaign_id", campaignId),
      new ChildTable("relation_versions", "relation_id", relationId),
      new ChildTable("entity_embeddings", "session_id", sessionId),
      new ChildTable("annotations", "campaign_id", campaignId),
      new ChildTable("proposals", "campaign_id", campaignId),
      new ChildTable("proposal_votes", "proposal_id", proposalId),
    };

    // Sanity: every table holds exactly the row we seeded before the delete.
    for (ChildTable child : children) {
      assertThat(count(child)).as("seeded %s", child.table()).isEqualTo(1L);
    }

    adapter.deleteById(campaignId);

    assertThat(adapter.findById(campaignId)).isEmpty();
    for (ChildTable child : children) {
      assertThat(count(child)).as("orphaned rows in %s after cascade", child.table()).isZero();
    }
  }

  private long count(ChildTable child) {
    Long c =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM " + child.table() + " WHERE " + child.column() + " = ?",
            Long.class,
            child.value());
    return c == null ? 0L : c;
  }

  // ---------------------------------------------------------------------------
  // Seed helpers
  // ---------------------------------------------------------------------------

  private UUID insertUser() {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO users (id, email, password_hash, is_admin, force_password_change, created_at) VALUES (?, ?, 'hash', FALSE, FALSE, now())",
        id,
        id + "@example.com");
    return id;
  }

  private UUID insertCampaign(UUID createdBy) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO campaigns (id, name, created_by, created_at) VALUES (?, 'Cascade Test', ?, now())",
        id,
        createdBy);
    jdbcTemplate.update(
        "INSERT INTO campaign_members (id, campaign_id, user_id, role, joined_at) VALUES (?, ?, ?, 'gm', now())",
        UUID.randomUUID(),
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

  private void insertNarrativeBlock(UUID sessionId) {
    jdbcTemplate.update(
        "INSERT INTO narrative_blocks (id, session_id, raw_summary_text, token_count, created_at) VALUES (?, ?, 'text', 1, now())",
        UUID.randomUUID(),
        sessionId);
  }

  private UUID insertEntity(String table, UUID campaignId, UUID ownerId, UUID sessionId) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO "
            + table
            + " (id, campaign_id, owner_id, name, created_at, created_in_session_id) VALUES (?, ?, ?, 'Entity', now(), ?)",
        id,
        campaignId,
        ownerId,
        sessionId);
    return id;
  }

  private void insertVersion(String table, String parentColumn, UUID parentId, UUID sessionId) {
    jdbcTemplate.update(
        "INSERT INTO "
            + table
            + " (id, "
            + parentColumn
            + ", session_id, version_number, full_snapshot, created_at) VALUES (?, ?, ?, 1, '{}', now())",
        UUID.randomUUID(),
        parentId,
        sessionId);
  }

  private void insertEmbedding(UUID entityId, UUID sessionId) {
    jdbcTemplate.update(
        "INSERT INTO entity_embeddings (id, entity_type, entity_id, session_id, embedding, created_at) VALUES (?, 'actor', ?, ?, ?::vector, now())",
        UUID.randomUUID(),
        entityId,
        sessionId,
        zeroVector());
  }

  private void insertAnnotation(UUID campaignId, UUID entityId, UUID authorId) {
    jdbcTemplate.update(
        "INSERT INTO annotations (id, campaign_id, entity_type, entity_id, author_id, content, created_at) VALUES (?, ?, 'actor', ?, ?, 'note', now())",
        UUID.randomUUID(),
        campaignId,
        entityId,
        authorId);
  }

  private UUID insertProposal(UUID campaignId, UUID authorId) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO proposals (id, campaign_id, author_id, status, created_at) VALUES (?, ?, ?, 'open', now())",
        id,
        campaignId,
        authorId);
    return id;
  }

  private void insertProposalVote(UUID proposalId, UUID voterId) {
    jdbcTemplate.update(
        "INSERT INTO proposal_votes (id, proposal_id, voter_id, vote, created_at) VALUES (?, ?, ?, 'cosign', now())",
        UUID.randomUUID(),
        proposalId,
        voterId);
  }

  /** Builds a zero vector literal matching the parameterized embedding column dimension. */
  private String zeroVector() {
    Integer dimension =
        jdbcTemplate.queryForObject(
            "SELECT atttypmod FROM pg_attribute WHERE attrelid = 'entity_embeddings'::regclass AND attname = 'embedding'",
            Integer.class);
    int dim = dimension == null ? 0 : dimension;
    return IntStream.range(0, dim)
        .mapToObj(i -> "0")
        .reduce((a, b) -> a + "," + b)
        .map(body -> "[" + body + "]")
        .orElse("[]");
  }
}
