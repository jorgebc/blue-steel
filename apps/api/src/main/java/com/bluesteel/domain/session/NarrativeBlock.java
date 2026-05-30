package com.bluesteel.domain.session;

import java.time.Instant;
import java.util.UUID;

/** Write-once record of the raw narrative summary submitted for a session. */
public class NarrativeBlock {

  private final UUID id;
  private final UUID sessionId;
  private final String rawSummaryText;
  private final int tokenCount;
  private final Instant createdAt;

  private NarrativeBlock(
      UUID id, UUID sessionId, String rawSummaryText, int tokenCount, Instant createdAt) {
    if (rawSummaryText == null || rawSummaryText.isBlank()) {
      throw new IllegalArgumentException("rawSummaryText must not be blank");
    }
    if (tokenCount < 0) {
      throw new IllegalArgumentException("tokenCount must not be negative");
    }
    this.id = id;
    this.sessionId = sessionId;
    this.rawSummaryText = rawSummaryText;
    this.tokenCount = tokenCount;
    this.createdAt = createdAt;
  }

  /** Creates a new write-once narrative block. */
  public static NarrativeBlock create(
      UUID id, UUID sessionId, String rawSummaryText, int tokenCount, Instant createdAt) {
    return new NarrativeBlock(id, sessionId, rawSummaryText, tokenCount, createdAt);
  }

  public UUID id() {
    return id;
  }

  public UUID sessionId() {
    return sessionId;
  }

  public String rawSummaryText() {
    return rawSummaryText;
  }

  public int tokenCount() {
    return tokenCount;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
