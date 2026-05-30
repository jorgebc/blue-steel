package com.bluesteel.adapters.out.persistence.session;

import com.bluesteel.application.port.out.session.SessionRepository;
import com.bluesteel.domain.session.Session;
import com.bluesteel.domain.session.SessionStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
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
