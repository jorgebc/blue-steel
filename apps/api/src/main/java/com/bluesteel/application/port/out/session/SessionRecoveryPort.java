package com.bluesteel.application.port.out.session;

/**
 * Driven port for recovering sessions left in a terminal-pending state after an unclean shutdown or
 * processing timeout (D-074).
 */
public interface SessionRecoveryPort {

  /**
   * Transitions all sessions stuck in {@code processing} to {@code failed} with reason {@code
   * PIPELINE_INTERRUPTED}. Runs once at startup. Returns the number of rows updated, or {@code -1}
   * if the sessions table does not yet exist.
   */
  int recoverStuckSessions();

  /**
   * Transitions {@code processing} sessions whose {@code updated_at} is older than {@code
   * timeoutMinutes} to {@code failed} with reason {@code PIPELINE_TIMEOUT}. Runs on a recurring
   * schedule (contrast with {@link #recoverStuckSessions()} which runs only at startup). Returns
   * the number of rows updated, or {@code -1} if the sessions table does not yet exist.
   */
  int recoverTimedOutSessions(int timeoutMinutes);
}
