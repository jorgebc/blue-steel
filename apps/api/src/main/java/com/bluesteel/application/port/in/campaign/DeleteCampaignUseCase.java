package com.bluesteel.application.port.in.campaign;

import java.util.UUID;

/** Admin-only use case: permanently delete a campaign and all its data. */
public interface DeleteCampaignUseCase {

  /**
   * Deletes the campaign and all related data (members, sessions, world-state entities,
   * annotations, proposals). The caller must be an admin; the campaign must exist.
   *
   * @throws com.bluesteel.domain.exception.UnauthorizedException when {@code callerIsAdmin} is
   *     false
   * @throws com.bluesteel.domain.exception.CampaignNotFoundException when no campaign matches
   *     {@code campaignId}
   */
  void delete(UUID campaignId, UUID callerId, boolean callerIsAdmin);
}
