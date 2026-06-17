package com.bluesteel.adapters.out.persistence.query;

import com.bluesteel.application.model.query.Citation;
import com.bluesteel.application.model.query.QueryLogEntry;
import com.bluesteel.application.port.out.query.QueryLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * JPA-backed implementation of {@link QueryLogRepository}. Maps the {@code citations} list to/from
 * a JSONB column and prunes each campaign's log to a bounded retention on every write so DB growth
 * stays a parameter, not a rewrite (query.history.max-per-campaign).
 */
@Component
public class QueryLogPersistenceAdapter implements QueryLogRepository {

  private static final TypeReference<List<Citation>> CITATION_LIST = new TypeReference<>() {};

  private final QueryLogJpaRepository jpaRepository;
  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;
  private final int maxPerCampaign;

  public QueryLogPersistenceAdapter(
      @Lazy QueryLogJpaRepository jpaRepository,
      @Lazy JdbcTemplate jdbcTemplate,
      ObjectMapper objectMapper,
      @Value("${query.history.max-per-campaign:500}") int maxPerCampaign) {
    this.jpaRepository = jpaRepository;
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
    this.maxPerCampaign = maxPerCampaign;
  }

  @Override
  public void save(QueryLogEntry entry) {
    // Flush the insert before the raw-JDBC prune below: a JdbcTemplate query does not trigger
    // Hibernate's auto-flush, so without this the just-saved row would be invisible to the
    // retention DELETE and the log could transiently hold one row beyond the bound.
    jpaRepository.saveAndFlush(toEntity(entry));
    deleteOldestBeyond(entry.campaignId(), maxPerCampaign);
  }

  @Override
  public List<QueryLogEntry> findByCampaign(UUID campaignId, int offset, int limit) {
    return jpaRepository.findPage(campaignId, offset, limit).stream().map(this::toDomain).toList();
  }

  @Override
  public long countByCampaign(UUID campaignId) {
    return jpaRepository.countByCampaignId(campaignId);
  }

  @Override
  public void deleteOldestBeyond(UUID campaignId, int maxRows) {
    jdbcTemplate.update(
        """
        DELETE FROM query_log
        WHERE campaign_id = ?
          AND id NOT IN (
            SELECT id FROM query_log
            WHERE campaign_id = ?
            ORDER BY created_at DESC
            LIMIT ?
          )
        """,
        campaignId,
        campaignId,
        maxRows);
  }

  private QueryLogJpaEntity toEntity(QueryLogEntry e) {
    return new QueryLogJpaEntity(
        e.id(),
        e.campaignId(),
        e.askerId(),
        e.question(),
        e.answer(),
        writeCitations(e.citations()),
        e.createdAt());
  }

  private QueryLogEntry toDomain(QueryLogJpaEntity e) {
    return new QueryLogEntry(
        e.getId(),
        e.getCampaignId(),
        e.getAskerId(),
        e.getQuestion(),
        e.getAnswer(),
        readCitations(e.getCitations()),
        e.getCreatedAt());
  }

  private String writeCitations(List<Citation> citations) {
    try {
      return objectMapper.writeValueAsString(citations);
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("Failed to serialize citations to JSON", ex);
    }
  }

  private List<Citation> readCitations(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return objectMapper.readValue(json, CITATION_LIST);
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("Failed to deserialize citations from JSON", ex);
    }
  }
}
