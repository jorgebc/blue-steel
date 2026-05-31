package com.bluesteel.application.port.out.session;

import com.bluesteel.domain.session.Session;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Driven port for persisting and querying {@link Session} aggregates. */
public interface SessionRepository {

  /** Persists a new or updated session. */
  void save(Session session);

  /** Returns the session by id, or empty if not found. */
  Optional<Session> findById(UUID id);

  /**
   * Returns one offset-paginated page of the campaign's sessions, ordered by sequence number (nulls
   * last) then creation time. {@code page} is zero-based; {@code size} is the page length (D-055).
   */
  List<Session> findByCampaignId(UUID campaignId, int page, int size);

  /** Returns the total number of sessions in the campaign across all pages. */
  long countByCampaignId(UUID campaignId);

  /**
   * Returns the single active session for a campaign (in {@code processing} or {@code draft}
   * status), or empty if none exists. Backs the D-054 single-active-session enforcement.
   */
  Optional<Session> findActiveByCampaignId(UUID campaignId);

  /**
   * Returns {@code MAX(sequence_number) + 1} across committed sessions for the campaign (D-069).
   * Returns {@code 1} when no committed sessions exist. Must be called inside the commit
   * {@code @Transactional} boundary so the result is serialized by the one-active-draft lock.
   */
  int nextSequenceNumber(UUID campaignId);
}
