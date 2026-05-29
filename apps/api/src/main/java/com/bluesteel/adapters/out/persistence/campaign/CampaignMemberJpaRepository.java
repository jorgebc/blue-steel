package com.bluesteel.adapters.out.persistence.campaign;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

interface CampaignMemberJpaRepository extends JpaRepository<CampaignMemberJpaEntity, UUID> {

  Optional<CampaignMemberJpaEntity> findByCampaignIdAndUserId(UUID campaignId, UUID userId);

  @Modifying
  @Transactional
  void deleteByCampaignIdAndUserId(UUID campaignId, UUID userId);

  boolean existsByUserIdAndRole(UUID userId, String role);
}
