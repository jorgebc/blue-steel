package com.bluesteel.application.service.worldstate;

import com.bluesteel.application.model.worldstate.EntityDetailView;
import com.bluesteel.application.model.worldstate.EntityListFilter;
import com.bluesteel.application.model.worldstate.EntityListPage;
import com.bluesteel.application.port.in.worldstate.GetEntityDetailUseCase;
import com.bluesteel.application.port.in.worldstate.ListEntitiesUseCase;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.worldstate.WorldStateReadPort;
import com.bluesteel.domain.exception.EntityNotFoundException;
import com.bluesteel.domain.exception.UnauthorizedException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Read-only Exploration Mode access to committed world state (D-010). Authorizes the caller as a
 * campaign member on every call (D-043) and delegates to the read port; a missing entity yields
 * {@link EntityNotFoundException}.
 */
@Service
public class WorldStateExplorationService implements ListEntitiesUseCase, GetEntityDetailUseCase {

  private static final Logger log = LoggerFactory.getLogger(WorldStateExplorationService.class);
  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;

  private final CampaignMembershipPort membershipPort;
  private final WorldStateReadPort readPort;

  public WorldStateExplorationService(
      CampaignMembershipPort membershipPort, WorldStateReadPort readPort) {
    this.membershipPort = membershipPort;
    this.readPort = readPort;
  }

  @Override
  public EntityListPage list(
      String entityType, UUID campaignId, UUID callerId, int page, int size) {
    log.info("Listing {} entities campaignId={} callerId={}", entityType, campaignId, callerId);
    requireMember(campaignId, callerId);

    int safePage = Math.max(page, 0);
    int safeSize = size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

    return readPort.list(entityType, campaignId, EntityListFilter.none(), safePage, safeSize);
  }

  @Override
  public EntityDetailView getDetail(
      String entityType, UUID campaignId, UUID entityId, UUID callerId) {
    log.info(
        "Reading {} detail entityId={} campaignId={} callerId={}",
        entityType,
        entityId,
        campaignId,
        callerId);
    requireMember(campaignId, callerId);

    EntityDetailView detail = readPort.getWithHistory(entityType, campaignId, entityId);
    if (detail == null) {
      throw new EntityNotFoundException("%s not found: %s".formatted(entityType, entityId));
    }
    return detail;
  }

  private void requireMember(UUID campaignId, UUID callerId) {
    membershipPort
        .resolveRole(campaignId, callerId)
        .orElseThrow(() -> new UnauthorizedException("Caller is not a member of this campaign"));
  }
}
