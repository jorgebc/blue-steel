package com.bluesteel.adapters.in.web.session;

import com.bluesteel.domain.session.SessionStatus;
import java.time.Instant;
import java.util.UUID;

/** Response body for one row of the campaign session list (ARCHITECTURE.md §7.6). */
public record SessionSummaryResponse(
    UUID sessionId,
    SessionStatus status,
    Integer sequenceNumber,
    Instant committedAt,
    Instant createdAt) {}
