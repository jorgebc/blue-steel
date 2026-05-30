package com.bluesteel.domain.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bluesteel.domain.exception.InvalidSessionStateTransitionException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SessionTest {

  private Session session;

  @BeforeEach
  void setUp() {
    session =
        Session.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now());
  }

  @Test
  @DisplayName("should start in PENDING status after create")
  void create_startsPending() {
    assertThat(session.status()).isEqualTo(SessionStatus.PENDING);
    assertThat(session.failureReason()).isNull();
    assertThat(session.diffPayload()).isNull();
    assertThat(session.committedAt()).isNull();
    assertThat(session.sequenceNumber()).isNull();
  }

  @Test
  @DisplayName("should transition PENDING → PROCESSING on startProcessing")
  void startProcessing_fromPending_succeeds() {
    session.startProcessing();
    assertThat(session.status()).isEqualTo(SessionStatus.PROCESSING);
  }

  @Test
  @DisplayName("should transition PROCESSING → DRAFT and store diff payload on toDraft")
  void toDraft_fromProcessing_storesDiffPayload() {
    session.startProcessing();
    session.toDraft("{\"cards\":[]}");
    assertThat(session.status()).isEqualTo(SessionStatus.DRAFT);
    assertThat(session.diffPayload()).isEqualTo("{\"cards\":[]}");
  }

  @Test
  @DisplayName("should transition PENDING → FAILED and record reason on markFailed")
  void markFailed_fromPending_recordsReason() {
    session.markFailed("PIPELINE_NOT_IMPLEMENTED");
    assertThat(session.status()).isEqualTo(SessionStatus.FAILED);
    assertThat(session.failureReason()).isEqualTo("PIPELINE_NOT_IMPLEMENTED");
  }

  @Test
  @DisplayName("should transition PROCESSING → FAILED and record reason on markFailed")
  void markFailed_fromProcessing_recordsReason() {
    session.startProcessing();
    session.markFailed("PIPELINE_ERROR");
    assertThat(session.status()).isEqualTo(SessionStatus.FAILED);
    assertThat(session.failureReason()).isEqualTo("PIPELINE_ERROR");
  }

  @Test
  @DisplayName("should transition DRAFT → DISCARDED and clear diff payload on discard")
  void discard_fromDraft_clearsDiffPayload() {
    session.startProcessing();
    session.toDraft("{\"cards\":[]}");
    session.discard();
    assertThat(session.status()).isEqualTo(SessionStatus.DISCARDED);
    assertThat(session.diffPayload()).isNull();
  }

  @Test
  @DisplayName("should transition DRAFT → COMMITTED and set committedAt on commit")
  void commit_fromDraft_setsCommittedAt() {
    session.startProcessing();
    session.toDraft("{\"cards\":[]}");
    session.commit();
    assertThat(session.status()).isEqualTo(SessionStatus.COMMITTED);
    assertThat(session.committedAt()).isNotNull();
  }

  @Test
  @DisplayName("should throw when startProcessing is called from PROCESSING")
  void startProcessing_fromProcessing_throws() {
    session.startProcessing();
    assertThatThrownBy(session::startProcessing)
        .isInstanceOf(InvalidSessionStateTransitionException.class);
  }

  @Test
  @DisplayName("should throw when startProcessing is called from DRAFT")
  void startProcessing_fromDraft_throws() {
    session.startProcessing();
    session.toDraft("{}");
    assertThatThrownBy(session::startProcessing)
        .isInstanceOf(InvalidSessionStateTransitionException.class);
  }

  @Test
  @DisplayName("should throw when toDraft is called from PENDING")
  void toDraft_fromPending_throws() {
    assertThatThrownBy(() -> session.toDraft("{}"))
        .isInstanceOf(InvalidSessionStateTransitionException.class);
  }

  @Test
  @DisplayName("should throw when toDraft is called from DRAFT")
  void toDraft_fromDraft_throws() {
    session.startProcessing();
    session.toDraft("{}");
    assertThatThrownBy(() -> session.toDraft("{}"))
        .isInstanceOf(InvalidSessionStateTransitionException.class);
  }

  @Test
  @DisplayName("should throw when markFailed is called from DRAFT")
  void markFailed_fromDraft_throws() {
    session.startProcessing();
    session.toDraft("{}");
    assertThatThrownBy(() -> session.markFailed("reason"))
        .isInstanceOf(InvalidSessionStateTransitionException.class);
  }

  @Test
  @DisplayName("should throw when markFailed is called from COMMITTED")
  void markFailed_fromCommitted_throws() {
    session.startProcessing();
    session.toDraft("{}");
    session.commit();
    assertThatThrownBy(() -> session.markFailed("reason"))
        .isInstanceOf(InvalidSessionStateTransitionException.class);
  }

  @Test
  @DisplayName("should throw when markFailed is called from DISCARDED")
  void markFailed_fromDiscarded_throws() {
    session.startProcessing();
    session.toDraft("{}");
    session.discard();
    assertThatThrownBy(() -> session.markFailed("reason"))
        .isInstanceOf(InvalidSessionStateTransitionException.class);
  }

  @Test
  @DisplayName("should throw when discard is called from PENDING")
  void discard_fromPending_throws() {
    assertThatThrownBy(session::discard).isInstanceOf(InvalidSessionStateTransitionException.class);
  }

  @Test
  @DisplayName("should throw when discard is called from COMMITTED")
  void discard_fromCommitted_throws() {
    session.startProcessing();
    session.toDraft("{}");
    session.commit();
    assertThatThrownBy(session::discard).isInstanceOf(InvalidSessionStateTransitionException.class);
  }

  @Test
  @DisplayName("should throw when commit is called from PENDING")
  void commit_fromPending_throws() {
    assertThatThrownBy(session::commit).isInstanceOf(InvalidSessionStateTransitionException.class);
  }

  @Test
  @DisplayName("should throw when commit is called from COMMITTED")
  void commit_fromCommitted_throws() {
    session.startProcessing();
    session.toDraft("{}");
    session.commit();
    assertThatThrownBy(session::commit).isInstanceOf(InvalidSessionStateTransitionException.class);
  }

  @Test
  @DisplayName("should throw when commit is called from DISCARDED")
  void commit_fromDiscarded_throws() {
    session.startProcessing();
    session.toDraft("{}");
    session.discard();
    assertThatThrownBy(session::commit).isInstanceOf(InvalidSessionStateTransitionException.class);
  }
}
