package com.bluesteel.application.model.session;

import com.bluesteel.domain.session.SessionStatus;
import java.util.UUID;

/** Read model returned by the session status use case. */
public record SessionStatusView(
    UUID sessionId, SessionStatus status, String failureReason, String message) {}
