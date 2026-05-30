package com.bluesteel.domain.exception;

import java.util.UUID;

/**
 * Thrown when a session submission is rejected because an active session already exists (D-054).
 */
public class ActiveSessionExistsException extends DomainException {

  private final UUID existingSessionId;

  public ActiveSessionExistsException(UUID existingSessionId) {
    super(
        existingSessionId != null
            ? "An active session already exists: " + existingSessionId
            : "An active session already exists for this campaign");
    this.existingSessionId = existingSessionId;
  }

  /** Returns the id of the existing active session, or {@code null} if unknown (TOCTOU race). */
  public UUID existingSessionId() {
    return existingSessionId;
  }
}
