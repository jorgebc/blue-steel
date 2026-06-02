package com.bluesteel.adapters.out.persistence.worldstate;

import static org.assertj.core.api.Assertions.assertThat;

import com.bluesteel.TestcontainersPostgresBaseIT;
import com.bluesteel.application.model.worldstate.EntityDetailView;
import com.bluesteel.application.model.worldstate.EntityListFilter;
import com.bluesteel.application.model.worldstate.EntityListPage;
import com.bluesteel.application.model.worldstate.EntitySummaryView;
import com.bluesteel.application.model.worldstate.EntityVersionView;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Testcontainers integration test for {@link WorldStateReadAdapter} — verifies latest-version list
 * projection, full ordered version history in detail, campaign scoping, and offset paging against a
 * real Postgres instance (F4.1.2).
 */
@DisplayName("WorldStateReadAdapter (F4.1.2)")
class WorldStateReadAdapterIT extends TestcontainersPostgresBaseIT {

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private WorldStateReadAdapter sut;

  private UUID userId;
  private UUID campaignId;
  private UUID session1Id;
  private UUID session2Id;
  private UUID aldricId;
  private UUID seraphineId;
  private UUID tavernId;

  @BeforeEach
  void seedData() {
    userId = insertUser("readit-" + UUID.randomUUID() + "@test.com");
    campaignId = insertCampaign(userId);
    session1Id = insertSession(campaignId, userId, 1);
    session2Id = insertSession(campaignId, userId, 2);

    // Aldric: two actor versions (latest is v2 from session2)
    aldricId = insertActor(campaignId, userId, session1Id, "Aldric");
    insertActorVersion(aldricId, session1Id, 1, null, "{\"name\":\"Aldric\",\"role\":\"squire\"}");
    insertActorVersion(
        aldricId,
        session2Id,
        2,
        "{\"role\":\"knight\"}",
        "{\"name\":\"Aldric\",\"role\":\"knight\"}");

    // Seraphine: single version
    seraphineId = insertActor(campaignId, userId, session1Id, "Seraphine");
    insertActorVersion(seraphineId, session1Id, 1, null, "{\"name\":\"Seraphine\"}");

    // Tavern: two space versions
    tavernId = insertSpace(campaignId, userId, session1Id, "The Prancing Pony");
    insertSpaceVersion(tavernId, session1Id, 1, "{\"name\":\"The Prancing Pony\"}");
    insertSpaceVersion(tavernId, session2Id, 2, "{\"name\":\"The Prancing Pony\",\"burned\":true}");
  }

  @Test
  @DisplayName("should list one summary per actor carrying its latest version and total count")
  void list_returnsLatestVersionPerHeadWithTotalCount() {
    EntityListPage result = sut.list("actor", campaignId, EntityListFilter.none(), 0, 20);

    assertThat(result.totalCount()).isEqualTo(2);
    assertThat(result.page()).isZero();
    assertThat(result.items()).hasSize(2);

    // Ordered by name ascending: Aldric then Seraphine
    EntitySummaryView aldric = result.items().get(0);
    assertThat(aldric.entityId()).isEqualTo(aldricId);
    assertThat(aldric.entityType()).isEqualTo("actor");
    assertThat(aldric.name()).isEqualTo("Aldric");
    assertThat(aldric.latestVersionNumber()).isEqualTo(2);
    assertThat(aldric.currentSnapshot()).containsEntry("role", "knight");
    assertThat(aldric.lastUpdatedSessionId()).isEqualTo(session2Id);

    assertThat(result.items().get(1).name()).isEqualTo("Seraphine");
  }

  @Test
  @DisplayName("should offset-paginate the entity list with a stable order")
  void list_offsetPaginates() {
    EntityListPage firstPage = sut.list("actor", campaignId, EntityListFilter.none(), 0, 1);
    EntityListPage secondPage = sut.list("actor", campaignId, EntityListFilter.none(), 1, 1);

    assertThat(firstPage.items()).hasSize(1);
    assertThat(firstPage.items().get(0).name()).isEqualTo("Aldric");
    assertThat(firstPage.totalCount()).isEqualTo(2);

    assertThat(secondPage.items()).hasSize(1);
    assertThat(secondPage.items().get(0).name()).isEqualTo("Seraphine");
    assertThat(secondPage.totalCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("should return the full ordered version history with session sequence numbers")
  void getWithHistory_returnsOrderedVersions() {
    EntityDetailView detail = sut.getWithHistory("actor", campaignId, aldricId);

    assertThat(detail).isNotNull();
    assertThat(detail.entityId()).isEqualTo(aldricId);
    assertThat(detail.entityType()).isEqualTo("actor");
    assertThat(detail.name()).isEqualTo("Aldric");
    assertThat(detail.ownerId()).isEqualTo(userId);
    assertThat(detail.versions()).hasSize(2);

    EntityVersionView v1 = detail.versions().get(0);
    assertThat(v1.versionNumber()).isEqualTo(1);
    assertThat(v1.sessionId()).isEqualTo(session1Id);
    assertThat(v1.sessionSequenceNumber()).isEqualTo(1);
    assertThat(v1.changedFields()).isEmpty();
    assertThat(v1.fullSnapshot()).containsEntry("role", "squire");

    EntityVersionView v2 = detail.versions().get(1);
    assertThat(v2.versionNumber()).isEqualTo(2);
    assertThat(v2.sessionSequenceNumber()).isEqualTo(2);
    assertThat(v2.changedFields()).containsEntry("role", "knight");
    assertThat(v2.fullSnapshot()).containsEntry("role", "knight");
  }

  @Test
  @DisplayName("should scope list and detail to the requested campaign")
  void list_and_detail_scopedToCampaign() {
    UUID otherCampaignId = insertCampaign(userId);

    EntityListPage otherList = sut.list("actor", otherCampaignId, EntityListFilter.none(), 0, 20);
    assertThat(otherList.items()).isEmpty();
    assertThat(otherList.totalCount()).isZero();

    assertThat(sut.getWithHistory("actor", otherCampaignId, aldricId)).isNull();
  }

  @Test
  @DisplayName("should return null detail when the entity does not exist")
  void getWithHistory_unknownEntity_returnsNull() {
    assertThat(sut.getWithHistory("actor", campaignId, UUID.randomUUID())).isNull();
  }

  @Test
  @DisplayName("should list spaces with their latest version via the space table pair")
  void list_spaces_usesSpaceTablePair() {
    EntityListPage spaces = sut.list("space", campaignId, EntityListFilter.none(), 0, 20);

    assertThat(spaces.totalCount()).isEqualTo(1);
    EntitySummaryView tavern = spaces.items().get(0);
    assertThat(tavern.entityId()).isEqualTo(tavernId);
    assertThat(tavern.entityType()).isEqualTo("space");
    assertThat(tavern.latestVersionNumber()).isEqualTo(2);
    assertThat(tavern.currentSnapshot()).containsEntry("burned", true);
  }

  @Test
  @DisplayName("should filter the list by a case-insensitive name substring")
  void list_filtersByNameContains() {
    EntityListPage result = sut.list("actor", campaignId, new EntityListFilter("ald", null), 0, 20);

    assertThat(result.totalCount()).isEqualTo(1);
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).name()).isEqualTo("Aldric");
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private UUID insertUser(String email) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO users (id, email, password_hash, is_admin, force_password_change, created_at)"
            + " VALUES (?, ?, 'hash', FALSE, FALSE, now())",
        id,
        email);
    return id;
  }

  private UUID insertCampaign(UUID createdBy) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO campaigns (id, name, created_by, created_at)"
            + " VALUES (?, 'Test Campaign', ?, now())",
        id,
        createdBy);
    return id;
  }

  private UUID insertSession(UUID campaignId, UUID ownerId, int sequenceNumber) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO sessions (id, campaign_id, owner_id, status, sequence_number, created_at,"
            + " updated_at) VALUES (?, ?, ?, 'committed', ?, now(), now())",
        id,
        campaignId,
        ownerId,
        sequenceNumber);
    return id;
  }

  private UUID insertActor(UUID campaignId, UUID ownerId, UUID sessionId, String name) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO actors (id, campaign_id, owner_id, name, created_at, created_in_session_id)"
            + " VALUES (?, ?, ?, ?, now(), ?)",
        id,
        campaignId,
        ownerId,
        name,
        sessionId);
    return id;
  }

  private void insertActorVersion(
      UUID actorId, UUID sessionId, int versionNumber, String changedFieldsJson, String snapshot) {
    jdbcTemplate.update(
        "INSERT INTO actor_versions (id, actor_id, session_id, version_number, changed_fields,"
            + " full_snapshot, created_at) VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, now())",
        UUID.randomUUID(),
        actorId,
        sessionId,
        versionNumber,
        changedFieldsJson,
        snapshot);
  }

  private UUID insertSpace(UUID campaignId, UUID ownerId, UUID sessionId, String name) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO spaces (id, campaign_id, owner_id, name, created_at, created_in_session_id)"
            + " VALUES (?, ?, ?, ?, now(), ?)",
        id,
        campaignId,
        ownerId,
        name,
        sessionId);
    return id;
  }

  private void insertSpaceVersion(
      UUID spaceId, UUID sessionId, int versionNumber, String snapshot) {
    jdbcTemplate.update(
        "INSERT INTO space_versions (id, space_id, session_id, version_number, full_snapshot,"
            + " created_at) VALUES (?, ?, ?, ?, ?::jsonb, now())",
        UUID.randomUUID(),
        spaceId,
        sessionId,
        versionNumber,
        snapshot);
  }
}
