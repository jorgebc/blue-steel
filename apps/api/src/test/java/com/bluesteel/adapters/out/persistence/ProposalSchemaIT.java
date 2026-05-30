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

/**
 * Verifies that the F2.1.6 proposals + proposal_votes schema changesets apply correctly (D-016).
 */
@DisplayName("Proposals schema migration (F2.1.6)")
class ProposalSchemaIT extends TestcontainersPostgresBaseIT {

  @Autowired private JdbcTemplate jdbcTemplate;

  // -------------------------------------------------------------------------
  // Table existence
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("should create the proposals table")
  void proposalsTableExists() {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name = 'proposals'",
            Long.class);
    assertThat(count).isEqualTo(1L);
  }

  @Test
  @DisplayName("should create the proposal_votes table")
  void proposalVotesTableExists() {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name = 'proposal_votes'",
            Long.class);
    assertThat(count).isEqualTo(1L);
  }

  // -------------------------------------------------------------------------
  // proposals CHECK constraints
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("should reject an invalid proposal status via CHECK constraint")
  void proposalStatusCheckConstraintEnforced() {
    UUID userId = insertUser("prop-status@test.com");
    UUID campaignId = insertCampaign(userId);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO proposals
                      (id, campaign_id, author_id, status, created_at)
                    VALUES (?, ?, ?, 'invalid_status', now())
                    """,
                    UUID.randomUUID(),
                    campaignId,
                    userId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("should accept all valid proposal statuses")
  void proposalStatusAcceptsAllValidValues() {
    UUID userId = insertUser("prop-status-valid@test.com");
    UUID campaignId = insertCampaign(userId);

    for (String status : new String[] {"open", "cosigned", "approved", "rejected", "expired"}) {
      jdbcTemplate.update(
          """
          INSERT INTO proposals (id, campaign_id, author_id, status, created_at)
          VALUES (?, ?, ?, ?, now())
          """,
          UUID.randomUUID(),
          campaignId,
          userId,
          status);
    }

    Long count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM proposals WHERE campaign_id = ?", Long.class, campaignId);
    assertThat(count).isEqualTo(5L);
  }

  // -------------------------------------------------------------------------
  // UNIQUE (proposal_id, voter_id) on proposal_votes
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("should enforce UNIQUE (proposal_id, voter_id) — second identical vote must fail")
  void proposalVotesUniquePerVoterEnforced() {
    UUID authorId = insertUser("vote-unique@test.com");
    UUID voterId = insertUser("voter@test.com");
    UUID campaignId = insertCampaign(authorId);
    UUID proposalId = insertProposal(campaignId, authorId);

    jdbcTemplate.update(
        """
        INSERT INTO proposal_votes (id, proposal_id, voter_id, vote, created_at)
        VALUES (?, ?, ?, 'cosign', now())
        """,
        UUID.randomUUID(),
        proposalId,
        voterId);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO proposal_votes (id, proposal_id, voter_id, vote, created_at)
                    VALUES (?, ?, ?, 'approve', now())
                    """,
                    UUID.randomUUID(),
                    proposalId,
                    voterId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("should reject an invalid vote value via CHECK constraint")
  void proposalVoteCheckConstraintEnforced() {
    UUID authorId = insertUser("vote-check@test.com");
    UUID voterId = insertUser("vote-check-voter@test.com");
    UUID campaignId = insertCampaign(authorId);
    UUID proposalId = insertProposal(campaignId, authorId);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO proposal_votes (id, proposal_id, voter_id, vote, created_at)
                    VALUES (?, ?, ?, 'abstain', now())
                    """,
                    UUID.randomUUID(),
                    proposalId,
                    voterId))
        .isInstanceOf(DataIntegrityViolationException.class);
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

  private UUID insertProposal(UUID campaignId, UUID authorId) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO proposals (id, campaign_id, author_id, status, created_at) VALUES (?, ?, ?, 'open', now())",
        id,
        campaignId,
        authorId);
    return id;
  }
}
