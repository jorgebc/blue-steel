package com.bluesteel.application.port.in.user;

import com.bluesteel.application.model.user.InvitationResult;
import com.bluesteel.application.model.user.InvitePlatformUserCommand;

/**
 * Admin-only: creates a new platform user account or refreshes credentials for an existing one
 * (D-051, D-070, D-077).
 */
public interface InvitePlatformUserUseCase {

  InvitationResult invite(InvitePlatformUserCommand command);
}
