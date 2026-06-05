package com.bluesteel.application.port.out.annotation;

import com.bluesteel.domain.annotation.Annotation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for annotation aggregate. */
public interface AnnotationRepository {

  Annotation save(Annotation annotation);

  List<Annotation> findByEntityTypeAndEntityIdAndCampaignId(
      String entityType, UUID entityId, UUID campaignId);

  Optional<Annotation> findById(UUID annotationId);

  void deleteById(UUID annotationId);
}
