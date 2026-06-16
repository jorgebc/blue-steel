package com.bluesteel.application.model.commit;

import java.util.List;

/**
 * Commit payload submitted by the user after reviewing the structured diff (ARCHITECTURE §7.6,
 * D-076). Mirrors the camelCase JSON shape exactly — no {@code @JsonProperty} annotations. The
 * {@code addedEntities} list carries reviewer-added entities the extraction missed (F6.1, D-053).
 */
public record CommitPayload(
    List<CardDecision> cardDecisions,
    List<UncertainResolution> uncertainResolutions,
    List<AcknowledgedConflict> acknowledgedConflicts,
    List<AddedEntity> addedEntities) {

  public CommitPayload {
    addedEntities = addedEntities == null ? List.of() : addedEntities;
  }

  /**
   * Convenience constructor for payloads without added entities (e.g. existing call sites/tests).
   */
  public CommitPayload(
      List<CardDecision> cardDecisions,
      List<UncertainResolution> uncertainResolutions,
      List<AcknowledgedConflict> acknowledgedConflicts) {
    this(cardDecisions, uncertainResolutions, acknowledgedConflicts, List.of());
  }
}
