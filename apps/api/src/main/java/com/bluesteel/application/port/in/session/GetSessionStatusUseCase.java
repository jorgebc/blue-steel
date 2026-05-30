package com.bluesteel.application.port.in.session;

import com.bluesteel.application.model.session.SessionStatusView;
import java.util.UUID;

/** Driving port for reading the current status of a session. */
public interface GetSessionStatusUseCase {

  /** Returns the session status visible to any campaign member. */
  SessionStatusView getStatus(UUID sessionId, UUID callerId, UUID campaignId);
}
