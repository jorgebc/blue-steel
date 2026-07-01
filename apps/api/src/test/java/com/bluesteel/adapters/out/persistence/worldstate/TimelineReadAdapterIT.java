package com.bluesteel.adapters.out.persistence.worldstate;

import static org.assertj.core.api.Assertions.assertThat;

import com.bluesteel.TestcontainersPostgresBaseIT;
import com.bluesteel.application.model.worldstate.TimelineEntryView;
import com.bluesteel.application.model.worldstate.TimelineFilter;
import com.bluesteel.application.model.worldstate.TimelinePage;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Testcontainers integration test for {@link TimelineReadAdapter} (F4.2.2, F4.6.5) — verifies feed
 * ordering by session sequence and event id, keyset cursor continuation without overlap or gap, the
 * end-of-feed null cursor, and event-type / space / involved-actor filtering resolved through the
 * relational links ({@code events.event_type}, {@code events.space_id}, {@code
 * event_involved_actors}), plus campaign scoping against real Postgres.
 */
@DisplayName("TimelineReadAdapter (F4.2.2)")
class TimelineReadAdapterIT extends TestcontainersPostgresBaseIT {

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private TimelineReadAdapter sut;

  private UUID userId;
  private UUID campaignId;
  private UUID session1Id;
  private UUID session2Id;
  private UUID ambushId;
  private UUID coronationId;
  private UUID duelId;

  @BeforeEach
  void seedData() {
    userId = insertUser("timeline-" + UUID.randomUUID() + "@test.com");
    campaignId = insertCampaign(userId);
    session1Id = insertSession(campaignId, userId, 1);
    session2Id = insertSession(campaignId, userId, 2);

    // Structured links live relationally now (F4.6): actor/space heads + the join table.
    UUID aldric = insertActor(campaignId, userId, "Aldric");
    UUID seraphine = insertActor(campaignId, userId, "Seraphine");
    UUID mountainPass = insertSpace(campaignId, userId, "Mountain Pass");

    // Two events in session 1 (same sequence number -> ordered by event id), one in session 2.
    ambushId = insertEvent(campaignId, userId, session1Id, "Ambush at the Pass");
    setEventLinks(ambushId, mountainPass, "battle");
    addInvolvedActor(ambushId, aldric);
    addInvolvedActor(ambushId, seraphine);
    insertEventVersion(ambushId, session1Id, 1, "{\"name\":\"Ambush at the Pass\"}");

    coronationId = insertEvent(campaignId, userId, session1Id, "Coronation");
    setEventLinks(coronationId, null, "ceremony");
    addInvolvedActor(coronationId, aldric);
    insertEventVersion(coronationId, session1Id, 1, "{\"name\":\"Coronation\"}");

    duelId = insertEvent(campaignId, userId, session2Id, "Duel at Dawn");
    setEventLinks(duelId, null, "battle");
    addInvolvedActor(duelId, seraphine);
    insertEventVersion(duelId, session2Id, 1, "{\"name\":\"Duel at Dawn\"}");
  }

  @Test
  @DisplayName(
      "should return events ordered by session sequence then event id with relational link fields")
  void page_returnsOrderedFeedWithProjectedFields() {
    TimelinePage page = sut.page(campaignId, null, 20, TimelineFilter.none());

    assertThat(page.events()).hasSize(3);

    // Cross-session ordering is the deterministic contract: both session-1 events (sequence 1)
    // come before the session-2 event. The within-session tiebreaker is Postgres's own `e.id ASC`
    // (unsigned-byte UUID order), which does not match Java's signed UUID.compareTo — so we assert
    // the two session-1 events are present in either order rather than predicting their order here.
    // The keyset-continuation test verifies the tiebreaker is consistent and gap-free.
    TimelineEntryView first = page.events().get(0);
    TimelineEntryView middle = page.events().get(1);
    assertThat(first.sessionSequenceNumber()).isEqualTo(1);
    assertThat(middle.sessionSequenceNumber()).isEqualTo(1);
    assertThat(List.of(first.eventId(), middle.eventId()))
        .containsExactlyInAnyOrder(ambushId, coronationId);

    TimelineEntryView last = page.events().get(2);
    assertThat(last.eventId()).isEqualTo(duelId);
    assertThat(last.sessionSequenceNumber()).isEqualTo(2);
    assertThat(last.name()).isEqualTo("Duel at Dawn");
    assertThat(last.eventType()).isEqualTo("battle");
    assertThat(last.involvedActorNames()).containsExactly("Seraphine");
    assertThat(page.nextCursor()).isNull();
  }

  @Test
  @DisplayName("should continue from the cursor with no overlap or gap across pages")
  void page_keysetContinuationCoversFeedExactlyOnce() {
    TimelinePage first = sut.page(campaignId, null, 2, TimelineFilter.none());
    assertThat(first.events()).hasSize(2);
    assertThat(first.nextCursor()).isNotNull();

    TimelinePage second = sut.page(campaignId, first.nextCursor(), 2, TimelineFilter.none());
    assertThat(second.events()).hasSize(1);
    assertThat(second.nextCursor()).isNull();

    List<UUID> seen =
        java.util.stream.Stream.concat(first.events().stream(), second.events().stream())
            .map(TimelineEntryView::eventId)
            .toList();
    assertThat(seen).containsExactlyInAnyOrder(ambushId, coronationId, duelId);
    assertThat(seen).doesNotHaveDuplicates();
  }

  @Test
  @DisplayName("should return a null next cursor when the last page is not full")
  void page_returnsNullCursorOnFinalPage() {
    TimelinePage page = sut.page(campaignId, null, 20, TimelineFilter.none());

    assertThat(page.events()).hasSize(3);
    assertThat(page.nextCursor()).isNull();
  }

  @Test
  @DisplayName("should filter the feed by event type from the event_type column")
  void page_filtersByEventType() {
    TimelinePage battles = sut.page(campaignId, null, 20, new TimelineFilter(null, null, "battle"));

    assertThat(battles.events()).hasSize(2);
    assertThat(battles.events())
        .extracting(TimelineEntryView::eventId)
        .containsExactlyInAnyOrder(ambushId, duelId);
  }

  @Test
  @DisplayName("should filter by event type as a case-insensitive substring")
  void page_filtersByEventTypeSubstring() {
    TimelinePage battles = sut.page(campaignId, null, 20, new TimelineFilter(null, null, "BATT"));

    assertThat(battles.events())
        .extracting(TimelineEntryView::eventId)
        .containsExactlyInAnyOrder(ambushId, duelId);
  }

  @Test
  @DisplayName("should filter the feed by an involved actor substring via the join table")
  void page_filtersByActor() {
    TimelinePage seraphineEvents =
        sut.page(campaignId, null, 20, new TimelineFilter("seraph", null, null));

    assertThat(seraphineEvents.events())
        .extracting(TimelineEntryView::eventId)
        .containsExactlyInAnyOrder(ambushId, duelId);
  }

  @Test
  @DisplayName("should filter the feed by space name via events.space_id")
  void page_filtersBySpace() {
    TimelinePage atThePass =
        sut.page(campaignId, null, 20, new TimelineFilter(null, "mountain", null));

    assertThat(atThePass.events()).extracting(TimelineEntryView::eventId).containsExactly(ambushId);
    assertThat(atThePass.events().get(0).spaceName()).isEqualTo("Mountain Pass");
  }

  @Test
  @DisplayName("should scope the feed to the requested campaign")
  void page_scopedToCampaign() {
    UUID otherCampaignId = insertCampaign(userId);

    TimelinePage page = sut.page(otherCampaignId, null, 20, TimelineFilter.none());

    assertThat(page.events()).isEmpty();
    assertThat(page.nextCursor()).isNull();
  }

  @Test
  @DisplayName("should exclude events whose latest version belongs to an uncommitted session")
  void page_excludesUncommittedSessions() {
    UUID draftSession = insertDraftSession(campaignId, userId);
    UUID secretEvent = insertEvent(campaignId, userId, draftSession, "Secret Meeting");
    insertEventVersion(secretEvent, draftSession, 1, "{\"name\":\"Secret Meeting\"}");

    TimelinePage page = sut.page(campaignId, null, 20, TimelineFilter.none());

    assertThat(page.events())
        .extracting(TimelineEntryView::eventId)
        .doesNotContain(secretEvent)
        .containsExactlyInAnyOrder(ambushId, coronationId, duelId);
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

  private UUID insertDraftSession(UUID campaignId, UUID ownerId) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO sessions (id, campaign_id, owner_id, status, created_at, updated_at)"
            + " VALUES (?, ?, ?, 'draft', now(), now())",
        id,
        campaignId,
        ownerId);
    return id;
  }

  private UUID insertEvent(UUID campaignId, UUID ownerId, UUID sessionId, String name) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO events (id, campaign_id, owner_id, name, created_at, created_in_session_id)"
            + " VALUES (?, ?, ?, ?, now(), ?)",
        id,
        campaignId,
        ownerId,
        name,
        sessionId);
    return id;
  }

  private UUID insertActor(UUID campaignId, UUID ownerId, String name) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO actors (id, campaign_id, owner_id, name, created_at) VALUES (?, ?, ?, ?, now())",
        id,
        campaignId,
        ownerId,
        name);
    return id;
  }

  private UUID insertSpace(UUID campaignId, UUID ownerId, String name) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO spaces (id, campaign_id, owner_id, name, created_at) VALUES (?, ?, ?, ?, now())",
        id,
        campaignId,
        ownerId,
        name);
    return id;
  }

  private void setEventLinks(UUID eventId, UUID spaceId, String eventType) {
    jdbcTemplate.update(
        "UPDATE events SET space_id = ?, event_type = ? WHERE id = ?", spaceId, eventType, eventId);
  }

  private void addInvolvedActor(UUID eventId, UUID actorId) {
    jdbcTemplate.update(
        "INSERT INTO event_involved_actors (event_id, actor_id) VALUES (?, ?)", eventId, actorId);
  }

  private void insertEventVersion(
      UUID eventId, UUID sessionId, int versionNumber, String snapshot) {
    jdbcTemplate.update(
        "INSERT INTO event_versions (id, event_id, session_id, version_number, full_snapshot,"
            + " created_at) VALUES (?, ?, ?, ?, ?::jsonb, now())",
        UUID.randomUUID(),
        eventId,
        sessionId,
        versionNumber,
        snapshot);
  }
}
