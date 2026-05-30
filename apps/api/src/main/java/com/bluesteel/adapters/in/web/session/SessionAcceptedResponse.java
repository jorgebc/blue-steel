package com.bluesteel.adapters.in.web.session;

import com.bluesteel.domain.session.SessionStatus;
import java.util.UUID;

/** Response body returned when a session is accepted for ingestion. */
public record SessionAcceptedResponse(UUID sessionId, SessionStatus status) {}
