package com.bluesteel.application.port.in.session;

import java.util.UUID;

/** Driving port for discarding a draft session (GM-only). */
public interface DiscardSessionUseCase {

  /** Transitions the session from {@code DRAFT} to {@code DISCARDED}. */
  void discard(UUID sessionId, UUID callerId, UUID campaignId);
}
