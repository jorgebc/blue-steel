package com.bluesteel.adapters.out.persistence.query;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "query_log")
class QueryLogJpaEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "campaign_id", nullable = false)
  private UUID campaignId;

  @Column(name = "asker_id", nullable = false)
  private UUID askerId;

  @Column(name = "question", nullable = false)
  private String question;

  @Column(name = "answer", nullable = false)
  private String answer;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "citations", columnDefinition = "jsonb")
  private String citations;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected QueryLogJpaEntity() {}

  QueryLogJpaEntity(
      UUID id,
      UUID campaignId,
      UUID askerId,
      String question,
      String answer,
      String citations,
      Instant createdAt) {
    this.id = id;
    this.campaignId = campaignId;
    this.askerId = askerId;
    this.question = question;
    this.answer = answer;
    this.citations = citations;
    this.createdAt = createdAt;
  }

  UUID getId() {
    return id;
  }

  UUID getCampaignId() {
    return campaignId;
  }

  UUID getAskerId() {
    return askerId;
  }

  String getQuestion() {
    return question;
  }

  String getAnswer() {
    return answer;
  }

  String getCitations() {
    return citations;
  }

  Instant getCreatedAt() {
    return createdAt;
  }
}
