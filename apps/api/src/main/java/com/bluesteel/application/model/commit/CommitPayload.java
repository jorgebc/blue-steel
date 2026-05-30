package com.bluesteel.application.model.commit;

import java.util.List;

/**
 * Commit payload submitted by the user after reviewing the structured diff (ARCHITECTURE §7.6,
 * D-076). Mirrors the camelCase JSON shape exactly — no {@code @JsonProperty} annotations.
 */
public record CommitPayload(
    List<CardDecision> cardDecisions,
    List<UncertainResolution> uncertainResolutions,
    List<AcknowledgedConflict> acknowledgedConflicts) {}
