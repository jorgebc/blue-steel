package com.bluesteel.application.model.session;

import com.bluesteel.domain.session.SessionStatus;
import java.time.Instant;
import java.util.UUID;

/** Read model for one row of the campaign session list (ARCHITECTURE.md §7.6). */
public record SessionSummaryView(
    UUID sessionId,
    SessionStatus status,
    Integer sequenceNumber,
    Instant committedAt,
    Instant createdAt) {}
