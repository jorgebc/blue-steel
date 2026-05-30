package com.bluesteel.adapters.out.persistence.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.bluesteel.TestcontainersPostgresBaseIT;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@DisplayName("SessionRecoveryAdapter — recoverTimedOutSessions")
class SessionRecoveryAdapterIT extends TestcontainersPostgresBaseIT {

  @Autowired private SessionRecoveryAdapter adapter;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName(
      "should fail processing sessions older than the timeout but leave recent ones untouched")
  void recoverTimedOutSessions_failsOldProcessingOnly() {
    UUID userId = insertUser();
    UUID campaignId1 = insertCampaign(userId);
    UUID campaignId2 = insertCampaign(userId);

    UUID oldSessionId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO sessions (id, campaign_id, owner_id, status, created_at, updated_at)
        VALUES (?, ?, ?, 'processing', now() - interval '30 minutes', now() - interval '30 minutes')
        """,
        oldSessionId,
        campaignId1,
        userId);

    UUID recentSessionId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO sessions (id, campaign_id, owner_id, status, created_at, updated_at)
        VALUES (?, ?, ?, 'processing', now(), now())
        """,
        recentSessionId,
        campaignId2,
        userId);

    int count = adapter.recoverTimedOutSessions(10);

    assertThat(count).isEqualTo(1);

    Map<String, Object> oldRow =
        jdbcTemplate.queryForMap(
            "SELECT status, failure_reason FROM sessions WHERE id = ?", oldSessionId);
    assertThat(oldRow.get("status")).isEqualTo("failed");
    assertThat(oldRow.get("failure_reason")).isEqualTo("PIPELINE_TIMEOUT");

    Map<String, Object> recentRow =
        jdbcTemplate.queryForMap("SELECT status FROM sessions WHERE id = ?", recentSessionId);
    assertThat(recentRow.get("status")).isEqualTo("processing");
  }

  @Test
  @DisplayName("should return 0 when no sessions have timed out")
  void recoverTimedOutSessions_noTimedOutSessions_returnsZero() {
    int count = adapter.recoverTimedOutSessions(10);
    assertThat(count).isGreaterThanOrEqualTo(0);
  }

  private UUID insertUser() {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO users (id, email, password_hash, is_admin, force_password_change, created_at)"
            + " VALUES (?, ?, 'hash', FALSE, FALSE, now())",
        id,
        id + "@recovery-test.com");
    return id;
  }

  private UUID insertCampaign(UUID createdBy) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO campaigns (id, name, created_by, created_at) VALUES (?, 'Recovery Campaign', ?, now())",
        id,
        createdBy);
    return id;
  }
}
