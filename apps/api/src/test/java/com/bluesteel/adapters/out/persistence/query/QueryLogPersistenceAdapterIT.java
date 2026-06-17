package com.bluesteel.adapters.out.persistence.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.bluesteel.TestcontainersPostgresBaseIT;
import com.bluesteel.application.model.query.Citation;
import com.bluesteel.application.model.query.QueryLogEntry;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@DisplayName("QueryLogPersistenceAdapter")
@Transactional
class QueryLogPersistenceAdapterIT extends TestcontainersPostgresBaseIT {

  @Autowired private QueryLogPersistenceAdapter adapter;
  @Autowired private JdbcTemplate jdbcTemplate;

  private static final UUID CAMPAIGN_ID = UUID.randomUUID();

  @Test
  @DisplayName("should round-trip an entry preserving its citations through JSONB")
  void save_thenFind_preservesCitations() {
    UUID asker = insertUser();
    insertCampaign(asker);
    Citation c1 = new Citation(UUID.randomUUID(), 1, "Mira appears.");
    Citation c2 = new Citation(UUID.randomUUID(), 4, "Mira flees.");
    adapter.save(entry(asker, "Who is Mira?", "Mira is a rogue.", List.of(c1, c2), Instant.now()));

    List<QueryLogEntry> page = adapter.findByCampaign(CAMPAIGN_ID, 0, 10);

    assertThat(page).hasSize(1);
    QueryLogEntry found = page.get(0);
    assertThat(found.question()).isEqualTo("Who is Mira?");
    assertThat(found.answer()).isEqualTo("Mira is a rogue.");
    assertThat(found.askerId()).isEqualTo(asker);
    assertThat(found.citations()).containsExactly(c1, c2);
  }

  @Test
  @DisplayName("should return entries newest-first honouring offset and limit")
  void findByCampaign_pagesNewestFirst() {
    UUID asker = insertUser();
    insertCampaign(asker);
    Instant base = Instant.now().truncatedTo(ChronoUnit.MICROS);
    adapter.save(entry(asker, "q1", "a1", List.of(), base));
    adapter.save(entry(asker, "q2", "a2", List.of(), base.plusSeconds(10)));
    adapter.save(entry(asker, "q3", "a3", List.of(), base.plusSeconds(20)));

    List<QueryLogEntry> firstPage = adapter.findByCampaign(CAMPAIGN_ID, 0, 2);
    List<QueryLogEntry> secondPage = adapter.findByCampaign(CAMPAIGN_ID, 2, 2);

    assertThat(firstPage).extracting(QueryLogEntry::question).containsExactly("q3", "q2");
    assertThat(secondPage).extracting(QueryLogEntry::question).containsExactly("q1");
    assertThat(adapter.countByCampaign(CAMPAIGN_ID)).isEqualTo(3);
  }

  @Test
  @DisplayName("should prune all but the newest N entries for the campaign")
  void deleteOldestBeyond_keepsOnlyNewest() {
    UUID asker = insertUser();
    insertCampaign(asker);
    Instant base = Instant.now().truncatedTo(ChronoUnit.MICROS);
    adapter.save(entry(asker, "q1", "a1", List.of(), base));
    adapter.save(entry(asker, "q2", "a2", List.of(), base.plusSeconds(10)));
    adapter.save(entry(asker, "q3", "a3", List.of(), base.plusSeconds(20)));
    adapter.save(entry(asker, "q4", "a4", List.of(), base.plusSeconds(30)));

    adapter.deleteOldestBeyond(CAMPAIGN_ID, 2);

    assertThat(adapter.countByCampaign(CAMPAIGN_ID)).isEqualTo(2);
    assertThat(adapter.findByCampaign(CAMPAIGN_ID, 0, 10))
        .extracting(QueryLogEntry::question)
        .containsExactly("q4", "q3");
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private QueryLogEntry entry(
      UUID asker, String question, String answer, List<Citation> citations, Instant createdAt) {
    return new QueryLogEntry(
        UUID.randomUUID(),
        CAMPAIGN_ID,
        asker,
        question,
        answer,
        citations,
        createdAt.truncatedTo(ChronoUnit.MICROS));
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
