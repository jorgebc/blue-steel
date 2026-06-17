package com.bluesteel.application.model.query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One persisted Q&amp;A log entry: a successful grounded answer recorded for a campaign so the
 * history panel can revisit it (D-058). Append-only — never mutated after creation.
 */
public record QueryLogEntry(
    UUID id,
    UUID campaignId,
    UUID askerId,
    String question,
    String answer,
    List<Citation> citations,
    Instant createdAt) {

  public QueryLogEntry {
    if (question == null || question.isBlank()) {
      throw new IllegalArgumentException("question must not be blank");
    }
    if (answer == null || answer.isBlank()) {
      throw new IllegalArgumentException("answer must not be blank");
    }
    citations = citations == null ? List.of() : List.copyOf(citations);
  }
}
