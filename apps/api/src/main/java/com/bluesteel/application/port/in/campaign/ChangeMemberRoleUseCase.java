package com.bluesteel.application.port.in.campaign;

import com.bluesteel.application.model.campaign.ChangeMemberRoleCommand;

/** GM-only: changes a non-GM member's role within a campaign (D-015, D-043). */
public interface ChangeMemberRoleUseCase {

  void change(ChangeMemberRoleCommand command);
}
