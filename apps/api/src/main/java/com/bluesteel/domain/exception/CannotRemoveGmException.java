package com.bluesteel.domain.exception;

import java.util.UUID;

/** Thrown when an operation would remove or change the role of a campaign's GM (mapped to 422). */
public class CannotRemoveGmException extends RuntimeException {

  public CannotRemoveGmException(UUID userId) {
    super("Cannot remove or change the role of a GM: " + userId);
  }
}
