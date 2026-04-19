package com.bluesteel.application.port.out.session;

/**
 * Driven port for recovering sessions left in a terminal-pending state after an unclean shutdown
 * (D-074).
 */
public interface SessionRecoveryPort {

  /**
   * Transitions all sessions stuck in {@code processing} to {@code failed}. Returns the number of
   * rows updated, or {@code -1} if the sessions table does not yet exist.
   */
  int recoverStuckSessions();
}
