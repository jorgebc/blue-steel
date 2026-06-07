package com.bluesteel.application.port.in.query;

import com.bluesteel.application.model.query.QueryResponse;
import java.util.UUID;

/** Driving port for answering a free-text question against a campaign's world state (D-052). */
public interface AnswerQueryUseCase {

  /**
   * Answers {@code question} for the campaign on behalf of {@code callerId}. The caller must be a
   * member of the campaign (any role). Synchronous: blocks until an answer is produced or the
   * configured deadline elapses, in which case a {@link
   * com.bluesteel.domain.exception.QueryTimeoutException} is thrown (D-052).
   */
  QueryResponse answer(UUID campaignId, UUID callerId, String question);
}
