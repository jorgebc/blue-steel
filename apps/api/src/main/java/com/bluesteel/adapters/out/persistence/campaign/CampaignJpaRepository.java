package com.bluesteel.adapters.out.persistence.campaign;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CampaignJpaRepository extends JpaRepository<CampaignJpaEntity, UUID> {

  @Query(
      "SELECT c FROM CampaignJpaEntity c JOIN CampaignMemberJpaEntity m ON m.campaignId = c.id"
          + " WHERE m.userId = :userId")
  List<CampaignJpaEntity> findAllByMemberId(@Param("userId") UUID userId);
}
