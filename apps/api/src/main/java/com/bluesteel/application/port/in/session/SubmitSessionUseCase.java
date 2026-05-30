package com.bluesteel.application.port.in.session;

import com.bluesteel.application.model.session.SubmitSessionCommand;
import com.bluesteel.application.model.session.SubmitSessionResult;

/** Driving port for submitting a new session narrative for ingestion. */
public interface SubmitSessionUseCase {

  /**
   * Validates caller authorization and token budget, then persists the session and narrative block
   * in {@code PENDING} status and publishes a {@code SessionSubmittedEvent}.
   */
  SubmitSessionResult submit(SubmitSessionCommand command);
}
