package com.bluesteel.adapters.out.persistence.worldstate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bluesteel.TestcontainersPostgresBaseIT;
import com.bluesteel.adapters.out.persistence.campaign.CampaignPersistenceAdapter;
import com.bluesteel.adapters.out.persistence.session.SessionPersistenceAdapter;
import com.bluesteel.adapters.out.persistence.user.UserPersistenceAdapter;
import com.bluesteel.application.model.worldstate.CommittedEntityVersion;
import com.bluesteel.application.model.worldstate.EntityWriteCommand;
import com.bluesteel.application.model.worldstate.ResolvedEndpoint;
import com.bluesteel.domain.campaign.Campaign;
import com.bluesteel.domain.session.Session;
import com.bluesteel.domain.user.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@DisplayName("WorldStateAdapter")
class WorldStateAdapterIT extends TestcontainersPostgresBaseIT {

  @Autowired private WorldStateAdapter adapter;
  @Autowired private UserPersistenceAdapter userAdapter;
  @Autowired private CampaignPersistenceAdapter campaignAdapter;
  @Autowired private SessionPersistenceAdapter sessionAdapter;
  @Autowired private JdbcTemplate jdbcTemplate;

  private UUID campaignId;
  private UUID ownerId;
  private UUID sessionId;

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
  }

  @Test
  @DisplayName("should insert head and version 1 for a new entity")
  void writeEntity_newEntity_insertsHeadAndVersionOne() {
    EntityWriteCommand cmd =
        new EntityWriteCommand(
            "actor",
            null,
            campaignId,
            ownerId,
            "Aragorn",
            null,
            Map.of("name", "Aragorn", "description", "A ranger from the north"),
            sessionId);

    CommittedEntityVersion result = adapter.writeEntity(cmd);

    assertThat(result.entityType()).isEqualTo("actor");
    assertThat(result.entityId()).isNotNull();
    assertThat(result.entityVersionId()).isNotNull();
    assertThat(result.versionNumber()).isEqualTo(1);
    assertThat(result.contentToEmbed()).contains("Aragorn");
    assertThat(result.contentHash()).hasSize(64);
  }

  @Test
  @DisplayName("should append version 2 when writing to an existing entity head")
  void writeEntity_existingEntity_appendsNextVersion() {
    EntityWriteCommand firstCmd =
        new EntityWriteCommand(
            "actor",
            null,
            campaignId,
            ownerId,
            "Legolas",
            null,
            Map.of("name", "Legolas"),
            sessionId);
    CommittedEntityVersion v1 = adapter.writeEntity(firstCmd);

    EntityWriteCommand secondCmd =
        new EntityWriteCommand(
            "actor",
            v1.entityId(),
            campaignId,
            ownerId,
            "Legolas",
            Map.of("description", "Prince of Mirkwood"),
            Map.of("name", "Legolas", "description", "Prince of Mirkwood"),
            sessionId);
    CommittedEntityVersion v2 = adapter.writeEntity(secondCmd);

    assertThat(v2.entityId()).isEqualTo(v1.entityId());
    assertThat(v2.versionNumber()).isEqualTo(2);
    assertThat(v2.entityVersionId()).isNotEqualTo(v1.entityVersionId());
  }

  @Test
  @DisplayName("should persist JSONB snapshot and return non-empty contentHash")
  void writeEntity_jsonbRoundTrip_snapshotAndHashPresent() {
    Map<String, Object> snapshot = Map.of("name", "Gimli", "race", "Dwarf");
    EntityWriteCommand cmd =
        new EntityWriteCommand(
            "actor", null, campaignId, ownerId, "Gimli", null, snapshot, sessionId);

    CommittedEntityVersion result = adapter.writeEntity(cmd);

    assertThat(result.contentHash()).isNotBlank();
    assertThat(result.contentToEmbed()).contains("Gimli");
  }

  @Test
  @DisplayName("should return true when entity belongs to the campaign")
  void existsInCampaign_ownCampaign_returnsTrue() {
    EntityWriteCommand cmd =
        new EntityWriteCommand(
            "actor",
            null,
            campaignId,
            ownerId,
            "Boromir",
            null,
            Map.of("name", "Boromir"),
            sessionId);
    CommittedEntityVersion result = adapter.writeEntity(cmd);

    assertThat(adapter.existsInCampaign("actor", result.entityId(), campaignId)).isTrue();
  }

  @Test
  @DisplayName("should return false when entity belongs to a different campaign")
  void existsInCampaign_otherCampaign_returnsFalse() {
    EntityWriteCommand cmd =
        new EntityWriteCommand(
            "actor",
            null,
            campaignId,
            ownerId,
            "Faramir",
            null,
            Map.of("name", "Faramir"),
            sessionId);
    CommittedEntityVersion result = adapter.writeEntity(cmd);

    assertThat(adapter.existsInCampaign("actor", result.entityId(), UUID.randomUUID())).isFalse();
  }

  @Test
  @DisplayName("should throw IllegalArgumentException for an unknown entity type")
  void writeEntity_unknownEntityType_throwsIllegalArgument() {
    EntityWriteCommand cmd =
        new EntityWriteCommand(
            "villain",
            null,
            campaignId,
            ownerId,
            "Sauron",
            null,
            Map.of("name", "Sauron"),
            sessionId);

    assertThatThrownBy(() -> adapter.writeEntity(cmd)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("should persist relation source/target endpoint columns on the head row (F4.3.4)")
  void writeEntity_relationWithEndpoints_persistsEndpointColumns() {
    UUID sourceId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();
    EntityWriteCommand cmd =
        new EntityWriteCommand(
            "relation",
            null,
            campaignId,
            ownerId,
            "Mira guides the party",
            null,
            Map.of("name", "Mira guides the party"),
            sessionId,
            sourceId,
            "actor",
            targetId,
            "space");

    CommittedEntityVersion result = adapter.writeEntity(cmd);

    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "SELECT source_entity_id, source_entity_type, target_entity_id, target_entity_type"
                + " FROM relations WHERE id = ?",
            result.entityId());
    assertThat(row.get("source_entity_id")).isEqualTo(sourceId);
    assertThat(row.get("source_entity_type")).isEqualTo("actor");
    assertThat(row.get("target_entity_id")).isEqualTo(targetId);
    assertThat(row.get("target_entity_type")).isEqualTo("space");
  }

  @Test
  @DisplayName("should leave relation endpoint columns null when not provided")
  void writeEntity_relationWithoutEndpoints_persistsNullColumns() {
    EntityWriteCommand cmd =
        new EntityWriteCommand(
            "relation",
            null,
            campaignId,
            ownerId,
            "An unresolved bond",
            null,
            Map.of("name", "An unresolved bond"),
            sessionId);

    CommittedEntityVersion result = adapter.writeEntity(cmd);

    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "SELECT source_entity_id, target_entity_id FROM relations WHERE id = ?",
            result.entityId());
    assertThat(row.get("source_entity_id")).isNull();
    assertThat(row.get("target_entity_id")).isNull();
  }

  @Test
  @DisplayName("should resolve an endpoint name to an actor, case-insensitively")
  void findEndpointByName_matchingActor_returnsActorEndpoint() {
    CommittedEntityVersion actor =
        adapter.writeEntity(
            new EntityWriteCommand(
                "actor",
                null,
                campaignId,
                ownerId,
                "Mira",
                null,
                Map.of("name", "Mira"),
                sessionId));

    Optional<ResolvedEndpoint> resolved = adapter.findEndpointByName(campaignId, "mira");

    assertThat(resolved).isPresent();
    assertThat(resolved.get().entityId()).isEqualTo(actor.entityId());
    assertThat(resolved.get().entityType()).isEqualTo("actor");
  }

  @Test
  @DisplayName("should resolve an endpoint name to a space when no actor matches")
  void findEndpointByName_matchingSpaceOnly_returnsSpaceEndpoint() {
    CommittedEntityVersion space =
        adapter.writeEntity(
            new EntityWriteCommand(
                "space",
                null,
                campaignId,
                ownerId,
                "Thornwick",
                null,
                Map.of("name", "Thornwick"),
                sessionId));

    Optional<ResolvedEndpoint> resolved = adapter.findEndpointByName(campaignId, "Thornwick");

    assertThat(resolved).isPresent();
    assertThat(resolved.get().entityId()).isEqualTo(space.entityId());
    assertThat(resolved.get().entityType()).isEqualTo("space");
  }

  @Test
  @DisplayName("should return empty when no actor or space matches the endpoint name")
  void findEndpointByName_noMatch_returnsEmpty() {
    assertThat(adapter.findEndpointByName(campaignId, "Nobody")).isEmpty();
  }
}
