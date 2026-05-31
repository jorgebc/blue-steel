package com.bluesteel.adapters.out.persistence.session;

import com.bluesteel.application.port.out.session.SessionRepository;
import com.bluesteel.domain.session.Session;
import com.bluesteel.domain.session.SessionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/** JPA-backed implementation of {@link SessionRepository}. */
@Component
public class SessionPersistenceAdapter implements SessionRepository {

  private final SessionJpaRepository jpaRepository;

  public SessionPersistenceAdapter(@Lazy SessionJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public void save(Session session) {
    jpaRepository.save(toEntity(session));
  }

  @Override
  public Optional<Session> findById(UUID id) {
    return jpaRepository.findById(id).map(this::toDomain);
  }

  @Override
  public Optional<Session> findActiveByCampaignId(UUID campaignId) {
    return jpaRepository.findActiveByCampaignId(campaignId).map(this::toDomain);
  }

  @Override
  public List<Session> findByCampaignId(UUID campaignId, int page, int size) {
    Sort sort = Sort.by(Sort.Order.asc("sequenceNumber").nullsLast(), Sort.Order.asc("createdAt"));
    return jpaRepository.findByCampaignId(campaignId, PageRequest.of(page, size, sort)).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public long countByCampaignId(UUID campaignId) {
    return jpaRepository.countByCampaignId(campaignId);
  }

  @Override
  public int nextSequenceNumber(UUID campaignId) {
    return jpaRepository.nextSequenceNumber(campaignId);
  }

  private Session toDomain(SessionJpaEntity e) {
    return Session.reconstitute(
        e.getId(),
        e.getCampaignId(),
        e.getOwnerId(),
        SessionStatus.valueOf(e.getStatus().toUpperCase()),
        e.getSequenceNumber(),
        e.getFailureReason(),
        e.getDiffPayload(),
        e.getCommittedAt(),
        e.getCreatedAt(),
        e.getUpdatedAt());
  }

  private SessionJpaEntity toEntity(Session s) {
    return new SessionJpaEntity(
        s.id(),
        s.campaignId(),
        s.ownerId(),
        s.status().name().toLowerCase(),
        s.sequenceNumber(),
        s.failureReason(),
        s.diffPayload(),
        s.committedAt(),
        s.createdAt(),
        s.updatedAt());
  }
}
