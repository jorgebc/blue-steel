package com.bluesteel.adapters.out.persistence.campaign;

import com.bluesteel.application.port.out.campaign.CampaignRepository;
import com.bluesteel.domain.campaign.Campaign;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** JPA-backed implementation of {@link CampaignRepository}. */
@Component
public class CampaignPersistenceAdapter implements CampaignRepository {

  private final CampaignJpaRepository jpaRepository;

  public CampaignPersistenceAdapter(@Lazy CampaignJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public void save(Campaign campaign) {
    jpaRepository.save(toEntity(campaign));
  }

  @Override
  public Optional<Campaign> findById(UUID id) {
    return jpaRepository.findById(id).map(this::toDomain);
  }

  @Override
  public List<Campaign> findAll() {
    return jpaRepository.findAll().stream().map(this::toDomain).toList();
  }

  @Override
  public List<Campaign> findAllByMemberId(UUID userId) {
    return jpaRepository.findAllByMemberId(userId).stream().map(this::toDomain).toList();
  }

  @Override
  public void deleteById(UUID id) {
    jpaRepository.deleteById(id);
  }

  private Campaign toDomain(CampaignJpaEntity e) {
    return Campaign.create(e.getId(), e.getName(), e.getCreatedBy(), e.getCreatedAt());
  }

  private CampaignJpaEntity toEntity(Campaign c) {
    return new CampaignJpaEntity(c.id(), c.name(), c.createdBy(), c.createdAt());
  }
}
