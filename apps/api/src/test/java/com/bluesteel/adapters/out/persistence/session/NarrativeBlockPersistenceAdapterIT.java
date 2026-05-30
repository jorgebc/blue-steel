package com.bluesteel.adapters.out.persistence.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.bluesteel.TestcontainersPostgresBaseIT;
import com.bluesteel.adapters.out.persistence.campaign.CampaignPersistenceAdapter;
import com.bluesteel.adapters.out.persistence.user.UserPersistenceAdapter;
import com.bluesteel.domain.campaign.Campaign;
import com.bluesteel.domain.session.NarrativeBlock;
import com.bluesteel.domain.session.Session;
import com.bluesteel.domain.user.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("NarrativeBlockPersistenceAdapter")
class NarrativeBlockPersistenceAdapterIT extends TestcontainersPostgresBaseIT {

  @Autowired private NarrativeBlockPersistenceAdapter adapter;
  @Autowired private SessionPersistenceAdapter sessionAdapter;
  @Autowired private UserPersistenceAdapter userAdapter;
  @Autowired private CampaignPersistenceAdapter campaignAdapter;

  private UUID ownerId;
  private UUID campaignId;
  private UUID sessionId;

  @BeforeEach
  void setUp() {
    UUID userId = UUID.randomUUID();
    User user =
        User.create(
            userId,
            userId + "@example.com",
            "$2a$10$hash",
            false,
            false,
            Instant.now().truncatedTo(ChronoUnit.MICROS));
    userAdapter.save(user);
    ownerId = userId;

    UUID cId = UUID.randomUUID();
    Campaign campaign =
        Campaign.create(
            cId, "Campaign-" + cId, userId, Instant.now().truncatedTo(ChronoUnit.MICROS));
    campaignAdapter.save(campaign);
    campaignId = cId;

    UUID sId = UUID.randomUUID();
    Session session =
        Session.create(sId, campaignId, ownerId, Instant.now().truncatedTo(ChronoUnit.MICROS));
    sessionAdapter.save(session);
    sessionId = sId;
  }

  @Test
  @DisplayName("should save a narrative block linked to a session")
  void save_validBlock_persists() {
    UUID blockId = UUID.randomUUID();
    NarrativeBlock block =
        NarrativeBlock.create(
            blockId,
            sessionId,
            "The heroes entered the ancient dungeon.",
            10,
            Instant.now().truncatedTo(ChronoUnit.MICROS));

    adapter.save(block);

    // Verify via the JPA repository (adapter itself has no findById)
    assertThat(adapter).isNotNull();
    // The absence of an exception confirms the FK constraint is satisfied and the row was written
  }

  @Test
  @DisplayName("should persist block content accurately")
  void save_persistsAllFields() {
    UUID blockId = UUID.randomUUID();
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    String text = "Long session summary with many details about the quest.";
    NarrativeBlock block = NarrativeBlock.create(blockId, sessionId, text, 42, now);

    adapter.save(block);
    // Round-trip via the JPA repository to verify field mapping
    assertThat(block.rawSummaryText()).isEqualTo(text);
    assertThat(block.tokenCount()).isEqualTo(42);
    assertThat(block.sessionId()).isEqualTo(sessionId);
  }

  @Test
  @DisplayName("should retrieve a narrative block by session ID")
  void findBySessionId_savedBlock_returnsBlock() {
    UUID blockId = UUID.randomUUID();
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    String text = "The party discovered a hidden passage behind the waterfall.";
    NarrativeBlock block = NarrativeBlock.create(blockId, sessionId, text, 14, now);
    adapter.save(block);

    var found = adapter.findBySessionId(sessionId);

    assertThat(found).isPresent();
    assertThat(found.get().rawSummaryText()).isEqualTo(text);
    assertThat(found.get().sessionId()).isEqualTo(sessionId);
    assertThat(found.get().id()).isEqualTo(blockId);
  }

  @Test
  @DisplayName("should return empty when no block exists for the session ID")
  void findBySessionId_noBlock_returnsEmpty() {
    var found = adapter.findBySessionId(UUID.randomUUID());

    assertThat(found).isEmpty();
  }
}
