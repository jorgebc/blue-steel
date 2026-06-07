package com.bluesteel.adapters.in.web.query;

import java.util.UUID;

/** A single citation linking a claim in the answer back to the session it derives from (D-003). */
public record CitationResponse(UUID sessionId, int sequenceNumber, String snippet) {}
