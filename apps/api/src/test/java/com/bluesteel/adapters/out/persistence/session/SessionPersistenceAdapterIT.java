package com.bluesteel.adapters.out.persistence.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bluesteel.TestcontainersPostgresBaseIT;
import com.bluesteel.adapters.out.persistence.campaign.CampaignPersistenceAdapter;
import com.bluesteel.adapters.out.persistence.user.UserPersistenceAdapter;
import com.bluesteel.domain.campaign.Campaign;
import com.bluesteel.domain.session.Session;
import com.bluesteel.domain.session.SessionStatus;
import com.bluesteel.domain.user.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

@DisplayName("SessionPersistenceAdapter")
class SessionPersistenceAdapterIT extends TestcontainersPostgresBaseIT {

  @Autowired private SessionPersistenceAdapter adapter;
  @Autowired private UserPersistenceAdapter userAdapter;
  @Autowired private CampaignPersistenceAdapter campaignAdapter;

  private UUID ownerId;
  private UUID campaignId;

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
  }

  @Test
  @DisplayName("should save and find a session by id")
  void saveAndFindById() {
    UUID id = UUID.randomUUID();
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    Session session = Session.create(id, campaignId, ownerId, now);

    adapter.save(session);
    Optional<Session> found = adapter.findById(id);

    assertThat(found).isPresent();
    assertThat(found.get().id()).isEqualTo(id);
    assertThat(found.get().campaignId()).isEqualTo(campaignId);
    assertThat(found.get().ownerId()).isEqualTo(ownerId);
    assertThat(found.get().status()).isEqualTo(SessionStatus.PENDING);
    assertThat(found.get().failureReason()).isNull();
    assertThat(found.get().diffPayload()).isNull();
  }

  @Test
  @DisplayName("should return empty when session is not found by id")
  void findById_notFound_returnsEmpty() {
    assertThat(adapter.findById(UUID.randomUUID())).isEmpty();
  }

  @Test
  @DisplayName("should find active (processing) session by campaign id")
  void findActiveByCampaignId_processing_returnsSession() {
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    Session session = Session.create(UUID.randomUUID(), campaignId, ownerId, now);
    session.startProcessing();
    adapter.save(session);

    Optional<Session> found = adapter.findActiveByCampaignId(campaignId);

    assertThat(found).isPresent();
    assertThat(found.get().status()).isEqualTo(SessionStatus.PROCESSING);
  }

  @Test
  @DisplayName("should return empty when no active session exists for the campaign")
  void findActiveByCampaignId_noActive_returnsEmpty() {
    assertThat(adapter.findActiveByCampaignId(campaignId)).isEmpty();
  }

  @Test
  @DisplayName("should not return a failed session as active")
  void findActiveByCampaignId_failed_returnsEmpty() {
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    Session session = Session.create(UUID.randomUUID(), campaignId, ownerId, now);
    session.markFailed("TEST");
    adapter.save(session);

    assertThat(adapter.findActiveByCampaignId(campaignId)).isEmpty();
  }

  @Test
  @DisplayName("should persist status transitions")
  void save_afterTransition_persistsNewStatus() {
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    Session session = Session.create(UUID.randomUUID(), campaignId, ownerId, now);
    adapter.save(session);

    session.markFailed("PIPELINE_NOT_IMPLEMENTED");
    adapter.save(session);

    Optional<Session> found = adapter.findById(session.id());
    assertThat(found).isPresent();
    assertThat(found.get().status()).isEqualTo(SessionStatus.FAILED);
    assertThat(found.get().failureReason()).isEqualTo("PIPELINE_NOT_IMPLEMENTED");
  }

  @Test
  @DisplayName("should throw when saving a second active session for the same campaign (D-054)")
  void save_secondActiveSession_throwsDataIntegrityViolation() {
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    Session first = Session.create(UUID.randomUUID(), campaignId, ownerId, now);
    first.startProcessing();
    adapter.save(first);

    Session second = Session.create(UUID.randomUUID(), campaignId, ownerId, now);
    second.startProcessing();

    assertThatThrownBy(() -> adapter.save(second))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("should return 1 when no committed sessions exist for the campaign")
  void nextSequenceNumber_noneCommitted_returnsOne() {
    assertThat(adapter.nextSequenceNumber(campaignId)).isEqualTo(1);
  }

  @Test
  @DisplayName("should return MAX(sequence_number)+1 across committed sessions (D-069)")
  void nextSequenceNumber_withCommittedSessions_returnsMaxPlusOne() {
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

    for (int i = 1; i <= 3; i++) {
      Session s = Session.create(UUID.randomUUID(), campaignId, ownerId, now);
      s.startProcessing();
      s.toDraft("{}");
      s.commit(i);
      adapter.save(s);
    }

    assertThat(adapter.nextSequenceNumber(campaignId)).isEqualTo(4);
  }

  @Test
  @DisplayName("should not count non-committed sessions when computing next sequence number")
  void nextSequenceNumber_ignoresNonCommittedSessions() {
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

    Session committed = Session.create(UUID.randomUUID(), campaignId, ownerId, now);
    committed.startProcessing();
    committed.toDraft("{}");
    committed.commit(1);
    adapter.save(committed);

    Session failed = Session.create(UUID.randomUUID(), campaignId, ownerId, now);
    failed.markFailed("REASON");
    adapter.save(failed);

    assertThat(adapter.nextSequenceNumber(campaignId)).isEqualTo(2);
  }
}
