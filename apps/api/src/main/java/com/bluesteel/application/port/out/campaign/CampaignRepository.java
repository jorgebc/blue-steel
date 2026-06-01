package com.bluesteel.application.port.out.campaign;

import com.bluesteel.domain.campaign.Campaign;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence-layer contract for {@link Campaign} aggregates. */
public interface CampaignRepository {

  void save(Campaign campaign);

  Optional<Campaign> findById(UUID id);

  List<Campaign> findAll();

  /** Returns campaigns where the given user is a member. */
  List<Campaign> findAllByMemberId(UUID userId);

  /** Permanently deletes the campaign row; DB cascades remove all related data. */
  void deleteById(UUID id);
}
