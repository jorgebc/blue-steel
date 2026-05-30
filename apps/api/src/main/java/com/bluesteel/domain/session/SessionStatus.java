package com.bluesteel.domain.session;

/** Lifecycle states for a session ingestion run. */
public enum SessionStatus {
  PENDING,
  PROCESSING,
  DRAFT,
  COMMITTED,
  FAILED,
  DISCARDED
}
