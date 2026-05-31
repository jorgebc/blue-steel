package com.bluesteel.application.port.in.session;

import com.bluesteel.application.model.session.SessionDetailView;
import java.util.UUID;

/** Driving port for reading a single session's detail, visible to any campaign member. */
public interface GetSessionDetailUseCase {

  /** Returns the session detail; the session must belong to the given campaign. */
  SessionDetailView getDetail(UUID sessionId, UUID callerId, UUID campaignId);
}
