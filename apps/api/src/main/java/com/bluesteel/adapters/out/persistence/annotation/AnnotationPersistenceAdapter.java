package com.bluesteel.adapters.out.persistence.annotation;

import com.bluesteel.application.port.out.annotation.AnnotationRepository;
import com.bluesteel.domain.annotation.Annotation;
import com.bluesteel.domain.annotation.AnnotationEntityType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** JPA-backed implementation of {@link AnnotationRepository}. */
@Component
public class AnnotationPersistenceAdapter implements AnnotationRepository {

  private final AnnotationJpaRepository jpaRepository;

  public AnnotationPersistenceAdapter(@Lazy AnnotationJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public Annotation save(Annotation annotation) {
    return toDomain(jpaRepository.save(toEntity(annotation)));
  }

  @Override
  public List<Annotation> findByEntityTypeAndEntityIdAndCampaignId(
      String entityType, UUID entityId, UUID campaignId) {
    return jpaRepository
        .findByEntityTypeAndEntityIdAndCampaignId(entityType, entityId, campaignId)
        .stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public Optional<Annotation> findById(UUID annotationId) {
    return jpaRepository.findById(annotationId).map(this::toDomain);
  }

  @Override
  public void deleteById(UUID annotationId) {
    jpaRepository.deleteById(annotationId);
  }

  private Annotation toDomain(AnnotationJpaEntity e) {
    return Annotation.create(
        e.getId(),
        e.getCampaignId(),
        e.getEntityId(),
        AnnotationEntityType.valueOf(e.getEntityType()),
        e.getAuthorId(),
        e.getContent(),
        e.getCreatedAt());
  }

  private AnnotationJpaEntity toEntity(Annotation a) {
    return new AnnotationJpaEntity(
        a.id(),
        a.campaignId(),
        a.entityType().value(),
        a.entityId(),
        a.authorId(),
        a.content(),
        a.createdAt());
  }
}
