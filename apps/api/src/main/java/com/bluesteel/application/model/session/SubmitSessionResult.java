package com.bluesteel.application.model.session;

import com.bluesteel.domain.session.SessionStatus;
import java.util.UUID;

/** Result returned after a session is accepted for ingestion. */
public record SubmitSessionResult(UUID sessionId, SessionStatus status) {}
