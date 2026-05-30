package com.bluesteel.adapters.out.persistence.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "narrative_blocks")
class NarrativeBlockJpaEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "session_id", nullable = false)
  private UUID sessionId;

  @Column(name = "raw_summary_text", nullable = false)
  private String rawSummaryText;

  @Column(name = "token_count", nullable = false)
  private int tokenCount;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected NarrativeBlockJpaEntity() {}

  NarrativeBlockJpaEntity(
      UUID id, UUID sessionId, String rawSummaryText, int tokenCount, Instant createdAt) {
    this.id = id;
    this.sessionId = sessionId;
    this.rawSummaryText = rawSummaryText;
    this.tokenCount = tokenCount;
    this.createdAt = createdAt;
  }

  UUID getId() {
    return id;
  }

  UUID getSessionId() {
    return sessionId;
  }

  String getRawSummaryText() {
    return rawSummaryText;
  }

  int getTokenCount() {
    return tokenCount;
  }

  Instant getCreatedAt() {
    return createdAt;
  }
}
