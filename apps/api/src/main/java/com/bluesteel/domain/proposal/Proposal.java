package com.bluesteel.domain.proposal;

import com.bluesteel.domain.exception.InvalidProposalStateTransitionException;
import java.time.Instant;
import java.util.UUID;

/**
 * Proposal aggregate — a member-authored change to a single world-state entity, awaiting co-sign
 * and a GM decision. Identity and content fields are immutable; {@code status} and {@code
 * resultingEntityVersionId} are mutated through guarded transitions.
 *
 * <p>{@code sessionId} is provenance only — the session the author believes prompted the change. It
 * is not the session the resulting entity version is attributed to (D-107). The owner ({@link
 * #ownerId()}) is persisted in the {@code author_id} column (name divergence per D-021).
 */
public class Proposal {

  private final UUID id;
  private final UUID campaignId;
  private final ProposalTargetType targetType;
  private final UUID targetId;
  private final UUID ownerId;
  private final UUID sessionId;
  private final String proposedDelta;
  private final Instant expiresAt;
  private final Instant createdAt;

  private ProposalStatus status;
  private UUID resultingEntityVersionId;

  private Proposal(
      UUID id,
      UUID campaignId,
      ProposalTargetType targetType,
      UUID targetId,
      UUID ownerId,
      UUID sessionId,
      String proposedDelta,
      ProposalStatus status,
      Instant expiresAt,
      UUID resultingEntityVersionId,
      Instant createdAt) {
    this.id = id;
    this.campaignId = campaignId;
    this.targetType = targetType;
    this.targetId = targetId;
    this.ownerId = ownerId;
    this.sessionId = sessionId;
    this.proposedDelta = proposedDelta;
    this.status = status;
    this.expiresAt = expiresAt;
    this.resultingEntityVersionId = resultingEntityVersionId;
    this.createdAt = createdAt;
  }

  /**
   * Creates a new proposal in {@code OPEN} status with no resulting version yet. All references are
   * required; {@code sessionId} provenance is enforced non-null here even though the column is
   * nullable (D-107), and {@code proposedDelta} must be a non-blank JSON delta (D-104).
   */
  public static Proposal create(
      UUID id,
      UUID campaignId,
      ProposalTargetType targetType,
      UUID targetId,
      UUID ownerId,
      UUID sessionId,
      String proposedDelta,
      Instant expiresAt,
      Instant createdAt) {
    require(id != null, "id must not be null");
    require(campaignId != null, "campaignId must not be null");
    require(targetType != null, "targetType must not be null");
    require(targetId != null, "targetId must not be null");
    require(ownerId != null, "ownerId must not be null");
    require(sessionId != null, "sessionId must not be null");
    require(proposedDelta != null && !proposedDelta.isBlank(), "proposedDelta must not be blank");
    require(expiresAt != null, "expiresAt must not be null");
    require(createdAt != null, "createdAt must not be null");
    return new Proposal(
        id,
        campaignId,
        targetType,
        targetId,
        ownerId,
        sessionId,
        proposedDelta,
        ProposalStatus.OPEN,
        expiresAt,
        null,
        createdAt);
  }

  /** Reconstructs a proposal from persisted state (adapter use only). */
  public static Proposal reconstitute(
      UUID id,
      UUID campaignId,
      ProposalTargetType targetType,
      UUID targetId,
      UUID ownerId,
      UUID sessionId,
      String proposedDelta,
      ProposalStatus status,
      Instant expiresAt,
      UUID resultingEntityVersionId,
      Instant createdAt) {
    return new Proposal(
        id,
        campaignId,
        targetType,
        targetId,
        ownerId,
        sessionId,
        proposedDelta,
        status,
        expiresAt,
        resultingEntityVersionId,
        createdAt);
  }

  /** {@code OPEN → COSIGNED}. */
  public void coSign() {
    requireStatus(ProposalStatus.OPEN, "coSign");
    this.status = ProposalStatus.COSIGNED;
  }

  /** {@code COSIGNED → APPROVED}; stamps the resulting entity version id (D-107). */
  public void approve(UUID resultingEntityVersionId) {
    requireStatus(ProposalStatus.COSIGNED, "approve");
    if (resultingEntityVersionId == null) {
      throw new IllegalArgumentException("resultingEntityVersionId must not be null");
    }
    this.status = ProposalStatus.APPROVED;
    this.resultingEntityVersionId = resultingEntityVersionId;
  }

  /** {@code COSIGNED → REJECTED}. */
  public void reject() {
    requireStatus(ProposalStatus.COSIGNED, "reject");
    this.status = ProposalStatus.REJECTED;
  }

  /** {@code OPEN | COSIGNED → EXPIRED} (TTL sweep, D-105). */
  public void expire() {
    if (status != ProposalStatus.OPEN && status != ProposalStatus.COSIGNED) {
      throw new InvalidProposalStateTransitionException(
          "expire requires OPEN or COSIGNED but was " + status);
    }
    this.status = ProposalStatus.EXPIRED;
  }

  private void requireStatus(ProposalStatus required, String operation) {
    if (status != required) {
      throw new InvalidProposalStateTransitionException(
          operation + " requires " + required + " but was " + status);
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalArgumentException(message);
    }
  }

  public UUID id() {
    return id;
  }

  public UUID campaignId() {
    return campaignId;
  }

  public ProposalTargetType targetType() {
    return targetType;
  }

  public UUID targetId() {
    return targetId;
  }

  /** The authoring member; persisted in the {@code author_id} column (D-021). */
  public UUID ownerId() {
    return ownerId;
  }

  public UUID sessionId() {
    return sessionId;
  }

  public String proposedDelta() {
    return proposedDelta;
  }

  public ProposalStatus status() {
    return status;
  }

  public Instant expiresAt() {
    return expiresAt;
  }

  public UUID resultingEntityVersionId() {
    return resultingEntityVersionId;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
