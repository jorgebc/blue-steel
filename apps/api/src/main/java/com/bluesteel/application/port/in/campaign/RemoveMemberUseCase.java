package com.bluesteel.application.port.in.campaign;

import com.bluesteel.application.model.campaign.RemoveMemberCommand;

/** GM-only: removes a non-GM member from a campaign; removing the GM is rejected (D-043, D-061). */
public interface RemoveMemberUseCase {

  void remove(RemoveMemberCommand command);
}
