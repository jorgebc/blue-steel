package com.bluesteel.adapters.in.web.session;

import com.bluesteel.domain.session.SessionStatus;
import java.util.UUID;

/** Response body for the session status endpoint. */
public record SessionStatusResponse(
    UUID sessionId, SessionStatus status, String failureReason, String message) {}
