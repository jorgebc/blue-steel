package com.bluesteel.adapters.out.persistence.campaign;

import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.campaign.CampaignMembershipRepository;
import com.bluesteel.domain.campaign.CampaignMember;
import com.bluesteel.domain.campaign.CampaignRole;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * JPA-backed implementation of both {@link CampaignMembershipRepository} (write) and {@link
 * CampaignMembershipPort} (canonical role resolution, D-043).
 */
@Component
public class CampaignMembershipAdapter
    implements CampaignMembershipRepository, CampaignMembershipPort {

  private final CampaignMemberJpaRepository jpaRepository;

  public CampaignMembershipAdapter(@Lazy CampaignMemberJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public void save(CampaignMember member) {
    jpaRepository.save(toEntity(member));
  }

  @Override
  public Optional<CampaignRole> resolveRole(UUID campaignId, UUID userId) {
    return jpaRepository
        .findByCampaignIdAndUserId(campaignId, userId)
        .map(e -> CampaignRole.valueOf(e.getRole().toUpperCase()));
  }

  private CampaignMemberJpaEntity toEntity(CampaignMember m) {
    return new CampaignMemberJpaEntity(
        m.id(), m.campaignId(), m.userId(), m.role().name().toLowerCase(), m.joinedAt());
  }
}
