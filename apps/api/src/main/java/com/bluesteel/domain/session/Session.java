package com.bluesteel.domain.session;

import com.bluesteel.domain.exception.InvalidSessionStateTransitionException;
import java.time.Instant;
import java.util.UUID;

/**
 * Session aggregate — tracks the lifecycle of a single narrative ingestion run for a campaign.
 * Identity fields are immutable; status and payload fields are mutated through guarded transitions.
 */
public class Session {

  private final UUID id;
  private final UUID campaignId;
  private final UUID ownerId;
  private final Instant createdAt;

  private SessionStatus status;
  private Integer sequenceNumber;
  private String failureReason;
  private String diffPayload;
  private Instant committedAt;
  private Instant updatedAt;

  private Session(
      UUID id,
      UUID campaignId,
      UUID ownerId,
      SessionStatus status,
      Integer sequenceNumber,
      String failureReason,
      String diffPayload,
      Instant committedAt,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.campaignId = campaignId;
    this.ownerId = ownerId;
    this.status = status;
    this.sequenceNumber = sequenceNumber;
    this.failureReason = failureReason;
    this.diffPayload = diffPayload;
    this.committedAt = committedAt;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  /** Creates a new session in {@code PENDING} status. */
  public static Session create(UUID id, UUID campaignId, UUID ownerId, Instant now) {
    return new Session(
        id, campaignId, ownerId, SessionStatus.PENDING, null, null, null, null, now, now);
  }

  /** Reconstructs a session from persisted state (adapter use only). */
  public static Session reconstitute(
      UUID id,
      UUID campaignId,
      UUID ownerId,
      SessionStatus status,
      Integer sequenceNumber,
      String failureReason,
      String diffPayload,
      Instant committedAt,
      Instant createdAt,
      Instant updatedAt) {
    return new Session(
        id,
        campaignId,
        ownerId,
        status,
        sequenceNumber,
        failureReason,
        diffPayload,
        committedAt,
        createdAt,
        updatedAt);
  }

  /** {@code PENDING → PROCESSING}. */
  public void startProcessing() {
    requireStatus(SessionStatus.PENDING, "startProcessing");
    this.status = SessionStatus.PROCESSING;
    this.updatedAt = Instant.now();
  }

  /** {@code PROCESSING → DRAFT}; stores the generated diff payload. */
  public void toDraft(String diffPayload) {
    requireStatus(SessionStatus.PROCESSING, "toDraft");
    this.status = SessionStatus.DRAFT;
    this.diffPayload = diffPayload;
    this.updatedAt = Instant.now();
  }

  /** {@code PENDING | PROCESSING → FAILED}; records the failure reason. */
  public void markFailed(String reason) {
    if (status != SessionStatus.PENDING && status != SessionStatus.PROCESSING) {
      throw new InvalidSessionStateTransitionException(
          "markFailed requires PENDING or PROCESSING but was " + status);
    }
    this.status = SessionStatus.FAILED;
    this.failureReason = reason;
    this.updatedAt = Instant.now();
  }

  /** {@code DRAFT → DISCARDED}; clears the diff payload (GM-only soft delete). */
  public void discard() {
    requireStatus(SessionStatus.DRAFT, "discard");
    this.status = SessionStatus.DISCARDED;
    this.diffPayload = null;
    this.updatedAt = Instant.now();
  }

  /**
   * {@code DRAFT → COMMITTED}; records the assigned sequence number (D-069), stamps {@code
   * committedAt}, and clears {@code diffPayload}.
   */
  public void commit(int sequenceNumber) {
    requireStatus(SessionStatus.DRAFT, "commit");
    this.sequenceNumber = sequenceNumber;
    this.committedAt = Instant.now();
    this.status = SessionStatus.COMMITTED;
    this.diffPayload = null;
    this.updatedAt = this.committedAt;
  }

  private void requireStatus(SessionStatus required, String operation) {
    if (status != required) {
      throw new InvalidSessionStateTransitionException(
          operation + " requires " + required + " but was " + status);
    }
  }

  public UUID id() {
    return id;
  }

  public UUID campaignId() {
    return campaignId;
  }

  public UUID ownerId() {
    return ownerId;
  }

  public SessionStatus status() {
    return status;
  }

  public Integer sequenceNumber() {
    return sequenceNumber;
  }

  public String failureReason() {
    return failureReason;
  }

  public String diffPayload() {
    return diffPayload;
  }

  public Instant committedAt() {
    return committedAt;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }
}
