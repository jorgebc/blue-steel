package com.bluesteel.adapters.in.web.session;

import com.bluesteel.domain.session.SessionStatus;
import java.time.Instant;
import java.util.UUID;

/** Response body for the session detail endpoint (ARCHITECTURE.md §7.6). */
public record SessionDetailResponse(
    UUID sessionId,
    UUID campaignId,
    SessionStatus status,
    Integer sequenceNumber,
    String failureReason,
    Instant committedAt,
    Instant createdAt,
    Instant updatedAt,
    UUID narrativeBlockId) {}
