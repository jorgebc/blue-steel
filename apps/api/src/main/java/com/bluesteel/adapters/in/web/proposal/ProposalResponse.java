package com.bluesteel.adapters.in.web.proposal;

import com.bluesteel.application.model.proposal.ProposalView;
import com.bluesteel.domain.proposal.ProposalStatus;
import com.bluesteel.domain.proposal.ProposalTargetType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Response view of a proposal. {@code proposedDelta} is rehydrated from the stored JSON string into
 * an object so clients receive structured data; {@code sessionId} is provenance and {@code
 * resultingEntityVersionId} the approved-version back-reference (D-107).
 */
public record ProposalResponse(
    UUID proposalId,
    UUID campaignId,
    ProposalTargetType targetType,
    UUID targetId,
    UUID ownerId,
    ProposalStatus status,
    Object proposedDelta,
    UUID sessionId,
    UUID resultingEntityVersionId,
    Instant expiresAt,
    Instant createdAt) {

  /** Projects a {@link ProposalView} into the response, parsing the stored delta JSON. */
  public static ProposalResponse from(ProposalView view, ObjectMapper objectMapper) {
    return new ProposalResponse(
        view.proposalId(),
        view.campaignId(),
        view.targetType(),
        view.targetId(),
        view.ownerId(),
        view.status(),
        parseDelta(view.proposedDelta(), objectMapper),
        view.sessionId(),
        view.resultingEntityVersionId(),
        view.expiresAt(),
        view.createdAt());
  }

  private static Object parseDelta(String delta, ObjectMapper objectMapper) {
    if (delta == null) {
      return null;
    }
    try {
      return objectMapper.readValue(delta, Map.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Stored proposed_delta is not valid JSON", e);
    }
  }
}
