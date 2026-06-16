package com.bluesteel.adapters.out.persistence.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.bluesteel.TestcontainersPostgresBaseIT;
import com.bluesteel.adapters.out.persistence.campaign.CampaignMembershipAdapter;
import com.bluesteel.adapters.out.persistence.campaign.CampaignPersistenceAdapter;
import com.bluesteel.adapters.out.persistence.user.UserPersistenceAdapter;
import com.bluesteel.application.event.SessionCommittedEvent;
import com.bluesteel.application.model.commit.AddedEntity;
import com.bluesteel.application.model.commit.CommitPayload;
import com.bluesteel.application.model.session.CommitSessionCommand;
import com.bluesteel.application.model.session.DiffPayload;
import com.bluesteel.application.service.session.CommitService;
import com.bluesteel.domain.campaign.Campaign;
import com.bluesteel.domain.campaign.CampaignMember;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.session.Session;
import com.bluesteel.domain.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * End-to-end commit of reviewer-added entities (F6.1, D-053), wired against real adapters and a
 * live Postgres. Proves each {@code addedEntities} entry becomes a new head row + first version
 * stamped with the committing session, and that the versions ride {@link SessionCommittedEvent}.
 */
@DisplayName("Commit with addedEntities (end-to-end)")
@RecordApplicationEvents
class CommitAddedEntitiesFlowIT extends TestcontainersPostgresBaseIT {

  @Autowired private CommitService commitService;
  @Autowired private UserPersistenceAdapter userAdapter;
  @Autowired private CampaignPersistenceAdapter campaignAdapter;
  @Autowired private SessionPersistenceAdapter sessionAdapter;
  @Autowired private CampaignMembershipAdapter membershipAdapter;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ApplicationEvents events;

  private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

  private UUID campaignId;
  private UUID gmId;
  private UUID draftSessionId;

  @BeforeEach
  void setUp() throws Exception {
    gmId = savedUser();
    campaignId = savedCampaign(gmId);
    savedMember(campaignId, gmId, CampaignRole.GM);
    draftSessionId = savedDraftSession(campaignId, gmId);
  }

  @Test
  @DisplayName("creates a new actor + space head and first version stamped with the session")
  void commit_addedEntities_createsHeadsAndFirstVersions() throws Exception {
    CommitPayload payload =
        new CommitPayload(
            List.of(),
            List.of(),
            List.of(),
            List.of(
                new AddedEntity("actor", "Gandalf", Map.of("description", "A grey wizard")),
                new AddedEntity("space", "Rivendell", Map.of("description", "An elven refuge"))));

    commitService.commit(new CommitSessionCommand(gmId, campaignId, draftSessionId, payload));

    UUID actorId = headId("actors", "Gandalf");
    assertThat(actorId).isNotNull();
    assertThat(versionNumber("actor_versions", "actor_id", actorId)).isEqualTo(1);
    assertThat(versionSessionId("actor_versions", "actor_id", actorId)).isEqualTo(draftSessionId);
    assertThat(snapshot("actor_versions", "actor_id", actorId)).contains("A grey wizard");

    UUID spaceId = headId("spaces", "Rivendell");
    assertThat(spaceId).isNotNull();
    assertThat(versionNumber("space_versions", "space_id", spaceId)).isEqualTo(1);
    assertThat(versionSessionId("space_versions", "space_id", spaceId)).isEqualTo(draftSessionId);

    assertThat(events.stream(SessionCommittedEvent.class))
        .anyMatch(
            e ->
                e.sessionId().equals(draftSessionId)
                    && e.campaignId().equals(campaignId)
                    && e.committedVersions().size() == 2);
  }

  // --- assertion helpers --------------------------------------------------

  private UUID headId(String table, String name) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM " + table + " WHERE campaign_id = ? AND name = ?",
        UUID.class,
        campaignId,
        name);
  }

  private int versionNumber(String table, String fk, UUID entityId) {
    Integer n =
        jdbcTemplate.queryForObject(
            "SELECT version_number FROM " + table + " WHERE " + fk + " = ?",
            Integer.class,
            entityId);
    return n == null ? 0 : n;
  }

  private UUID versionSessionId(String table, String fk, UUID entityId) {
    return jdbcTemplate.queryForObject(
        "SELECT session_id FROM " + table + " WHERE " + fk + " = ?", UUID.class, entityId);
  }

  private String snapshot(String table, String fk, UUID entityId) {
    return jdbcTemplate.queryForObject(
        "SELECT full_snapshot::text FROM " + table + " WHERE " + fk + " = ?",
        String.class,
        entityId);
  }

  // --- seeding ------------------------------------------------------------

  private UUID savedUser() {
    UUID id = UUID.randomUUID();
    userAdapter.save(User.create(id, id + "@example.com", "$2a$10$hash", false, false, NOW));
    return id;
  }

  private UUID savedCampaign(UUID createdBy) {
    UUID id = UUID.randomUUID();
    campaignAdapter.save(Campaign.create(id, "Campaign-" + id, createdBy, NOW));
    return id;
  }

  private void savedMember(UUID campaign, UUID userId, CampaignRole role) {
    membershipAdapter.save(CampaignMember.create(UUID.randomUUID(), campaign, userId, role, NOW));
  }

  private UUID savedDraftSession(UUID campaign, UUID owner) throws Exception {
    UUID id = UUID.randomUUID();
    Session session = Session.create(id, campaign, owner, NOW);
    session.startProcessing();
    DiffPayload emptyDiff =
        new DiffPayload("header", List.of(), List.of(), List.of(), List.of(), List.of());
    session.toDraft(objectMapper.writeValueAsString(emptyDiff));
    sessionAdapter.save(session);
    return id;
  }
}
