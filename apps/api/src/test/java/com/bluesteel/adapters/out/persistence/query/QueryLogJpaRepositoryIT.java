package com.bluesteel.adapters.out.persistence.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.bluesteel.TestcontainersPostgresBaseIT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@DisplayName("QueryLogJpaRepository")
@Transactional
class QueryLogJpaRepositoryIT extends TestcontainersPostgresBaseIT {

  @Autowired private QueryLogJpaRepository repository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private static final UUID CAMPAIGN_ID = UUID.randomUUID();

  @Test
  @DisplayName("should return one page newest-first honouring offset and limit")
  void findPage_returnsNewestFirstWithOffsetAndLimit() {
    UUID asker = insertUser();
    insertCampaign(asker);
    Instant base = Instant.now().truncatedTo(ChronoUnit.MICROS);
    UUID oldest = save(asker, "q1", base);
    UUID middle = save(asker, "q2", base.plusSeconds(10));
    UUID newest = save(asker, "q3", base.plusSeconds(20));

    List<QueryLogJpaEntity> firstPage = repository.findPage(CAMPAIGN_ID, 0, 2);
    List<QueryLogJpaEntity> secondPage = repository.findPage(CAMPAIGN_ID, 2, 2);

    assertThat(firstPage).extracting(QueryLogJpaEntity::getId).containsExactly(newest, middle);
    assertThat(secondPage).extracting(QueryLogJpaEntity::getId).containsExactly(oldest);
  }

  @Test
  @DisplayName("should count only the campaign's entries")
  void countByCampaignId_countsOnlyCampaignRows() {
    UUID asker = insertUser();
    insertCampaign(asker);
    Instant base = Instant.now().truncatedTo(ChronoUnit.MICROS);
    save(asker, "q1", base);
    save(asker, "q2", base.plusSeconds(1));

    assertThat(repository.countByCampaignId(CAMPAIGN_ID)).isEqualTo(2);
    assertThat(repository.countByCampaignId(UUID.randomUUID())).isZero();
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private UUID save(UUID asker, String question, Instant createdAt) {
    UUID id = UUID.randomUUID();
    repository.save(
        new QueryLogJpaEntity(id, CAMPAIGN_ID, asker, question, "an answer", "[]", createdAt));
    return id;
  }

  private UUID insertUser() {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO users (id, email, password_hash, is_admin, force_password_change, created_at)"
            + " VALUES (?, ?, 'hash', FALSE, FALSE, now())",
        id,
        id + "@test.com");
    return id;
  }

  private void insertCampaign(UUID createdBy) {
    jdbcTemplate.update(
        "INSERT INTO campaigns (id, name, created_by, created_at) VALUES (?, 'Test', ?, now())"
            + " ON CONFLICT DO NOTHING",
        CAMPAIGN_ID,
        createdBy);
  }
}
