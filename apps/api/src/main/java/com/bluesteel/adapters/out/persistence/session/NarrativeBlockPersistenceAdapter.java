package com.bluesteel.adapters.out.persistence.session;

import com.bluesteel.application.port.out.session.NarrativeBlockRepository;
import com.bluesteel.domain.session.NarrativeBlock;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** JPA-backed implementation of {@link NarrativeBlockRepository}. */
@Component
public class NarrativeBlockPersistenceAdapter implements NarrativeBlockRepository {

  private final NarrativeBlockJpaRepository jpaRepository;

  public NarrativeBlockPersistenceAdapter(@Lazy NarrativeBlockJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public void save(NarrativeBlock block) {
    jpaRepository.save(toEntity(block));
  }

  private NarrativeBlock toDomain(NarrativeBlockJpaEntity e) {
    return NarrativeBlock.create(
        e.getId(), e.getSessionId(), e.getRawSummaryText(), e.getTokenCount(), e.getCreatedAt());
  }

  private NarrativeBlockJpaEntity toEntity(NarrativeBlock b) {
    return new NarrativeBlockJpaEntity(
        b.id(), b.sessionId(), b.rawSummaryText(), b.tokenCount(), b.createdAt());
  }
}
