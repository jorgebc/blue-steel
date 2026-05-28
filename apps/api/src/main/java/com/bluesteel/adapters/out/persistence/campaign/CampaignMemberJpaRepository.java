package com.bluesteel.adapters.out.persistence.campaign;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CampaignMemberJpaRepository extends JpaRepository<CampaignMemberJpaEntity, UUID> {

  Optional<CampaignMemberJpaEntity> findByCampaignIdAndUserId(UUID campaignId, UUID userId);
}
