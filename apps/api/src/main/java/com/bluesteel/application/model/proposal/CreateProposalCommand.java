package com.bluesteel.application.model.proposal;

import java.util.Map;
import java.util.UUID;

/**
 * Command to submit a new proposal. {@code targetType} is the raw client value (validated against
 * the {@code actor}/{@code space} v2 scope in the service, D-108); {@code sessionId} is the
 * creator-selected provenance session (D-107); {@code proposedDelta} is the flat {@code
 * {"field":"value"}} change map (D-104).
 */
public record CreateProposalCommand(
    UUID callerId,
    UUID campaignId,
    String targetType,
    UUID targetId,
    UUID sessionId,
    Map<String, Object> proposedDelta) {}
