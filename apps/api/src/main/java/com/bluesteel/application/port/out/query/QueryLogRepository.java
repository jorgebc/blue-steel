package com.bluesteel.application.port.out.query;

import com.bluesteel.application.model.query.QueryLogEntry;
import java.util.List;
import java.util.UUID;

/** Driven port for persisting and reading the append-only per-campaign Q&amp;A log (D-058). */
public interface QueryLogRepository {

  /** Persists a new log entry. */
  void save(QueryLogEntry entry);

  /** Returns one page of the campaign's entries, newest first. */
  List<QueryLogEntry> findByCampaign(UUID campaignId, int offset, int limit);

  /** Counts the campaign's entries. */
  long countByCampaign(UUID campaignId);

  /** Deletes all but the newest {@code maxRows} entries for the campaign (retention bound). */
  void deleteOldestBeyond(UUID campaignId, int maxRows);
}
