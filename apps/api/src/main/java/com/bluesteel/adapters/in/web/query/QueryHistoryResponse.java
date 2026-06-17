package com.bluesteel.adapters.in.web.query;

import com.bluesteel.application.model.query.QueryLogEntry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One logged Q&amp;A entry returned by the history endpoint, with its grounding citations (D-058).
 */
public record QueryHistoryResponse(
    UUID id, String question, String answer, List<CitationResponse> citations, Instant createdAt) {

  static QueryHistoryResponse from(QueryLogEntry entry) {
    List<CitationResponse> citations =
        entry.citations().stream()
            .map(c -> new CitationResponse(c.sessionId(), c.sequenceNumber(), c.snippet()))
            .toList();
    return new QueryHistoryResponse(
        entry.id(), entry.question(), entry.answer(), citations, entry.createdAt());
  }
}
