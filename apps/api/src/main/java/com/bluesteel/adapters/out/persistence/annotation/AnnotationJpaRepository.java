package com.bluesteel.adapters.out.persistence.annotation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AnnotationJpaRepository extends JpaRepository<AnnotationJpaEntity, UUID> {

  List<AnnotationJpaEntity> findByEntityTypeAndEntityIdAndCampaignId(
      String entityType, UUID entityId, UUID campaignId);
}
