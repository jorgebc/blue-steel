package com.bluesteel.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bluesteel.TestcontainersPostgresBaseIT;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Verifies that the F1.4 auth schema changesets apply correctly (D-025, D-059, D-061, D-077). */
@DisplayName("Auth schema migration")
class AuthSchemaIT extends TestcontainersPostgresBaseIT {

  @Autowired private JdbcTemplate jdbcTemplate;

  // -------------------------------------------------------------------------
  // Table existence
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("should create the users table")
  void usersTableExists() {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name = 'users'",
            Long.class);
    assertThat(count).isEqualTo(1L);
  }

  @Test
  @DisplayName("should create the campaigns table")
  void campaignsTableExists() {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name = 'campaigns'",
            Long.class);
    assertThat(count).isEqualTo(1L);
  }

  @Test
  @DisplayName("should create the campaign_members table")
  void campaignMembersTableExists() {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name = 'campaign_members'",
            Long.class);
    assertThat(count).isEqualTo(1L);
  }

  @Test
  @DisplayName("should create the refresh_tokens table")
  void refreshTokensTableExists() {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name = 'refresh_tokens'",
            Long.class);
    assertThat(count).isEqualTo(1L);
  }

  // -------------------------------------------------------------------------
  // users columns
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("should add force_password_change column to users (D-077)")
  void usersHasForcePasswordChangeColumn() {
    Long count =
        jdbcTemplate.queryForObject(
            """
            SELECT count(*) FROM information_schema.columns
            WHERE table_name = 'users' AND column_name = 'force_password_change'
            """,
            Long.class);
    assertThat(count).isEqualTo(1L);
  }

  // -------------------------------------------------------------------------
  // D-025: singleton admin partial unique index
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("should enforce singleton admin — second is_admin=TRUE insert must fail (D-025)")
  void singletonAdminPartialUniqueIndexEnforced() {
    // AdminBootstrapService seeds the first admin on ApplicationReadyEvent,
    // so any subsequent is_admin=TRUE insert must violate the partial unique index.
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO users (id, email, password_hash, is_admin, force_password_change, created_at)
                    VALUES (?, 'admin2@example.com', 'hash', TRUE, FALSE, now())
                    """,
                    UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("should allow multiple non-admin users — partial index only covers is_admin=TRUE")
  void multipleNonAdminUsersAllowed() {
    jdbcTemplate.update(
        """
        INSERT INTO users (id, email, password_hash, is_admin, force_password_change, created_at)
        VALUES (?, 'user1@example.com', 'hash', FALSE, FALSE, now())
        """,
        UUID.randomUUID());
    jdbcTemplate.update(
        """
        INSERT INTO users (id, email, password_hash, is_admin, force_password_change, created_at)
        VALUES (?, 'user2@example.com', 'hash', FALSE, FALSE, now())
        """,
        UUID.randomUUID());

    Long count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM users WHERE is_admin = FALSE", Long.class);
    assertThat(count).isGreaterThanOrEqualTo(2L);
  }

  // -------------------------------------------------------------------------
  // D-061: campaign_members constraints
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("should enforce UNIQUE (campaign_id, user_id) on campaign_members (D-061)")
  void campaignMembersUniquePerUser() {
    UUID userId = UUID.randomUUID();
    UUID creatorId = UUID.randomUUID();
    UUID campaignId = UUID.randomUUID();

    jdbcTemplate.update(
        """
        INSERT INTO users (id, email, password_hash, is_admin, force_password_change, created_at)
        VALUES (?, 'creator@example.com', 'hash', FALSE, FALSE, now())
        """,
        creatorId);
    jdbcTemplate.update(
        """
        INSERT INTO users (id, email, password_hash, is_admin, force_password_change, created_at)
        VALUES (?, 'member@example.com', 'hash', FALSE, FALSE, now())
        """,
        userId);
    jdbcTemplate.update(
        "INSERT INTO campaigns (id, name, created_by, created_at) VALUES (?, 'Camp', ?, now())",
        campaignId,
        creatorId);
    jdbcTemplate.update(
        """
        INSERT INTO campaign_members (id, campaign_id, user_id, role, joined_at)
        VALUES (?, ?, ?, 'player', now())
        """,
        UUID.randomUUID(),
        campaignId,
        userId);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO campaign_members (id, campaign_id, user_id, role, joined_at)
                    VALUES (?, ?, ?, 'editor', now())
                    """,
                    UUID.randomUUID(),
                    campaignId,
                    userId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("should enforce singleton GM per campaign via partial unique index (D-061)")
  void singletonGmPerCampaignEnforced() {
    UUID gm1Id = UUID.randomUUID();
    UUID gm2Id = UUID.randomUUID();
    UUID creatorId = UUID.randomUUID();
    UUID campaignId = UUID.randomUUID();

    for (Object[] row :
        List.of(
            new Object[] {creatorId, "gm-creator@test.com"},
            new Object[] {gm1Id, "gm1@test.com"},
            new Object[] {gm2Id, "gm2@test.com"})) {
      jdbcTemplate.update(
          """
          INSERT INTO users (id, email, password_hash, is_admin, force_password_change, created_at)
          VALUES (?, ?, 'hash', FALSE, FALSE, now())
          """,
          row[0],
          row[1]);
    }
    jdbcTemplate.update(
        "INSERT INTO campaigns (id, name, created_by, created_at) VALUES (?, 'Camp2', ?, now())",
        campaignId,
        creatorId);
    jdbcTemplate.update(
        "INSERT INTO campaign_members (id, campaign_id, user_id, role, joined_at) VALUES (?, ?, ?, 'gm', now())",
        UUID.randomUUID(),
        campaignId,
        gm1Id);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO campaign_members (id, campaign_id, user_id, role, joined_at) VALUES (?, ?, ?, 'gm', now())",
                    UUID.randomUUID(),
                    campaignId,
                    gm2Id))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("should reject invalid campaign member role via CHECK constraint (D-061)")
  void campaignMembersRoleCheckConstraintEnforced() {
    UUID userId = UUID.randomUUID();
    UUID creatorId = UUID.randomUUID();
    UUID campaignId = UUID.randomUUID();

    jdbcTemplate.update(
        """
        INSERT INTO users (id, email, password_hash, is_admin, force_password_change, created_at)
        VALUES (?, 'chk-creator@test.com', 'hash', FALSE, FALSE, now())
        """,
        creatorId);
    jdbcTemplate.update(
        """
        INSERT INTO users (id, email, password_hash, is_admin, force_password_change, created_at)
        VALUES (?, 'chk-user@test.com', 'hash', FALSE, FALSE, now())
        """,
        userId);
    jdbcTemplate.update(
        "INSERT INTO campaigns (id, name, created_by, created_at) VALUES (?, 'Camp3', ?, now())",
        campaignId,
        creatorId);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO campaign_members (id, campaign_id, user_id, role, joined_at)
                    VALUES (?, ?, ?, 'superuser', now())
                    """,
                    UUID.randomUUID(),
                    campaignId,
                    userId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // -------------------------------------------------------------------------
  // D-059: refresh_tokens schema
  // -------------------------------------------------------------------------

  @Test
  @DisplayName(
      "should create refresh_tokens with family_id, token_hash, used_at, expires_at (D-059)")
  void refreshTokensHasRequiredColumns() {
    List<String> expected = List.of("family_id", "token_hash", "used_at", "expires_at");
    for (String col : expected) {
      Long count =
          jdbcTemplate.queryForObject(
              "SELECT count(*) FROM information_schema.columns WHERE table_name = 'refresh_tokens' AND column_name = ?",
              Long.class,
              col);
      assertThat(count).as("column refresh_tokens.%s", col).isEqualTo(1L);
    }
  }
}
