package com.bluesteel.application.port.in.session;

import com.bluesteel.application.model.session.DiffPayload;
import java.util.UUID;

/** Returns the structured diff for a session that is in {@code DRAFT} status. */
public interface GetSessionDiffUseCase {

  /**
   * @param callerId the authenticated user's id
   * @param campaignId scopes the authorization check
   * @param sessionId the target session
   * @return the deserialized {@link DiffPayload}
   */
  DiffPayload getDiff(UUID callerId, UUID campaignId, UUID sessionId);
}
