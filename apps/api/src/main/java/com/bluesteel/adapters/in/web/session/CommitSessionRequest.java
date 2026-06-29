package com.bluesteel.adapters.in.web.session;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Request DTO for {@code POST .../sessions/{sid}/commit}. */
public record CommitSessionRequest(
    @NotEmpty List<@Valid CardDecisionRequest> cardDecisions,
    List<@Valid UncertainResolutionRequest> uncertainResolutions,
    List<AcknowledgedConflictRequest> acknowledgedConflicts,
    List<@Valid AddedEntityRequest> addedEntities) {

  /** Decision for a single diff card. */
  public record CardDecisionRequest(UUID cardId, String action, Map<String, Object> editedFields) {

    /** {@code editedFields} must be non-empty when {@code action == "edit"} (D-076). */
    @AssertTrue(message = "editedFields must be non-empty when action is edit")
    public boolean isEditedFieldsValidForAction() {
      if ("edit".equalsIgnoreCase(action)) {
        return editedFields != null && !editedFields.isEmpty();
      }
      return true;
    }
  }

  /** Resolution for an UNCERTAIN entity card. */
  public record UncertainResolutionRequest(UUID cardId, String resolution, UUID matchedEntityId) {

    /** {@code matchedEntityId} must be non-null when {@code resolution == "MATCH"} (D-079). */
    @AssertTrue(message = "matchedEntityId must be provided when resolution is MATCH")
    public boolean isMatchedEntityIdValidForResolution() {
      if ("MATCH".equalsIgnoreCase(resolution)) {
        return matchedEntityId != null;
      }
      return true;
    }
  }

  /** Acknowledgement of a detected conflict card. */
  public record AcknowledgedConflictRequest(UUID conflictId) {}

  /** A reviewer-added entity the extraction missed (F6.1, D-053). */
  public record AddedEntityRequest(
      @NotBlank @Size(max = 200) String entityType,
      @NotBlank @Size(max = 200) String name,
      Map<String, Object> fields) {}
}
