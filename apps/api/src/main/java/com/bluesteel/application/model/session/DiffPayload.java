package com.bluesteel.application.model.session;

import java.util.List;

/**
 * Canonical diff payload persisted as JSONB in {@code sessions.diff_payload} and returned by the
 * diff retrieval endpoint (ARCHITECTURE §7.6, D-076).
 */
public record DiffPayload(
    String narrativeSummaryHeader,
    List<DiffCard> actors,
    List<DiffCard> spaces,
    List<DiffCard> events,
    List<DiffCard> relations,
    List<ConflictCard> detectedConflicts) {}
