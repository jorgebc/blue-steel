package com.bluesteel.application.service.worldstate;

import com.bluesteel.application.model.worldstate.RelationDetailView;
import com.bluesteel.application.model.worldstate.RelationSummaryView;
import com.bluesteel.application.port.in.worldstate.GetRelationDetailUseCase;
import com.bluesteel.application.port.in.worldstate.ListRelationsUseCase;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.worldstate.RelationReadPort;
import com.bluesteel.domain.exception.EntityNotFoundException;
import com.bluesteel.domain.exception.UnauthorizedException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Read-only Exploration Mode access to the relations graph (D-010, D-030). Authorizes the caller as
 * a campaign member on every call (D-043) and delegates to the relation read port; a missing
 * relation yields {@link EntityNotFoundException}.
 */
@Service
public class RelationExplorationService implements ListRelationsUseCase, GetRelationDetailUseCase {

  private static final Logger log = LoggerFactory.getLogger(RelationExplorationService.class);

  private final CampaignMembershipPort membershipPort;
  private final RelationReadPort readPort;

  public RelationExplorationService(
      CampaignMembershipPort membershipPort, RelationReadPort readPort) {
    this.membershipPort = membershipPort;
    this.readPort = readPort;
  }

  @Override
  public List<RelationSummaryView> list(UUID campaignId, UUID callerId, UUID actorFilter) {
    log.info(
        "Listing relations campaignId={} callerId={} actorFilter={}",
        campaignId,
        callerId,
        actorFilter);
    requireMember(campaignId, callerId);
    return readPort.list(campaignId, actorFilter);
  }

  @Override
  public RelationDetailView getDetail(UUID campaignId, UUID relationId, UUID callerId) {
    log.info(
        "Reading relation detail relationId={} campaignId={} callerId={}",
        relationId,
        campaignId,
        callerId);
    requireMember(campaignId, callerId);

    RelationDetailView detail = readPort.getWithHistory(campaignId, relationId);
    if (detail == null) {
      throw new EntityNotFoundException("relation not found: " + relationId);
    }
    return detail;
  }

  private void requireMember(UUID campaignId, UUID callerId) {
    membershipPort
        .resolveRole(campaignId, callerId)
        .orElseThrow(() -> new UnauthorizedException("Caller is not a member of this campaign"));
  }
}
