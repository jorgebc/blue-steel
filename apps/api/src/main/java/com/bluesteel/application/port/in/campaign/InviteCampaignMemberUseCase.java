package com.bluesteel.application.port.in.campaign;

import com.bluesteel.application.model.campaign.InviteCampaignMemberCommand;

/** GM-only: invites a user to a campaign, creating the account if it does not yet exist (D-064). */
public interface InviteCampaignMemberUseCase {

  /** Returns {@code true} when a new platform account was created, {@code false} when reused. */
  boolean invite(InviteCampaignMemberCommand command);
}
