package com.bluesteel.application.model.session;

import com.bluesteel.domain.session.SessionStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Read model for a single session's detail (ARCHITECTURE.md §7.6), including the reference to its
 * narrative block and that block's raw summary text. Both {@code narrativeBlockId} and {@code
 * narrativeSummary} are null only if no block was ever stored for the session.
 */
public record SessionDetailView(
    UUID sessionId,
    UUID campaignId,
    SessionStatus status,
    Integer sequenceNumber,
    String failureReason,
    Instant committedAt,
    Instant createdAt,
    Instant updatedAt,
    UUID narrativeBlockId,
    String narrativeSummary) {}
