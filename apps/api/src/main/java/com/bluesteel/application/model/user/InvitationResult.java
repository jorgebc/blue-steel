package com.bluesteel.application.model.user;

/** Outcome of a platform-level user invitation. */
public enum InvitationResult {
  /** A new user account was created. */
  CREATED,
  /** An existing account's credentials were refreshed (re-invitation recovery, D-070). */
  REFRESHED
}
