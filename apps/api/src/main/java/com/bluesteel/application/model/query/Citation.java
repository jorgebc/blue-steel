package com.bluesteel.application.model.query;

import java.util.UUID;

/**
 * A grounding reference linking an answer passage to the session narrative that supports it
 * (D-003).
 */
public record Citation(UUID sessionId, int sequenceNumber, String snippet) {}
