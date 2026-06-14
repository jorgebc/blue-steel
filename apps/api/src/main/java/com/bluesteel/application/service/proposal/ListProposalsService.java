package com.bluesteel.application.service.proposal;

import com.bluesteel.application.model.proposal.ProposalFilter;
import com.bluesteel.application.model.proposal.ProposalListView;
import com.bluesteel.application.model.proposal.ProposalPage;
import com.bluesteel.application.model.proposal.ProposalView;
import com.bluesteel.application.port.in.proposal.ListProposalsUseCase;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.proposal.ProposalRepository;
import com.bluesteel.domain.exception.UnauthorizedException;
import com.bluesteel.domain.proposal.ProposalStatus;
import com.bluesteel.domain.proposal.ProposalTargetType;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Lists a campaign's proposals (offset-paginated) for any campaign member (D-055). */
@Service
public class ListProposalsService implements ListProposalsUseCase {

  private static final Logger log = LoggerFactory.getLogger(ListProposalsService.class);
  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;

  private final CampaignMembershipPort membershipPort;
  private final ProposalRepository proposalRepository;

  public ListProposalsService(
      CampaignMembershipPort membershipPort, ProposalRepository proposalRepository) {
    this.membershipPort = membershipPort;
    this.proposalRepository = proposalRepository;
  }

  @Override
  public ProposalListView list(
      UUID campaignId,
      UUID callerId,
      ProposalStatus status,
      ProposalTargetType targetType,
      UUID targetId,
      int page,
      int size) {
    log.info(
        "Listing proposals campaignId={} callerId={} status={} targetType={}",
        campaignId,
        callerId,
        status,
        targetType);

    membershipPort
        .resolveRole(campaignId, callerId)
        .orElseThrow(() -> new UnauthorizedException("Caller is not a member of this campaign"));

    if (targetType != null && targetId != null) {
      return listForTarget(campaignId, status, targetType, targetId);
    }

    int safePage = Math.max(page, 0);
    int safeSize = size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

    ProposalFilter filter = new ProposalFilter(status);
    List<ProposalView> proposals =
        proposalRepository
            .findByCampaign(campaignId, filter, new ProposalPage(safePage, safeSize))
            .stream()
            .map(ProposalView::from)
            .toList();
    long totalCount = proposalRepository.countByCampaign(campaignId, filter);

    return new ProposalListView(proposals, totalCount, safePage, safeSize);
  }

  /**
   * All proposals for a single target entity, newest first, returned unpaginated — an entity has at
   * most one open/cosigned proposal plus a short tail of terminal ones (D-106), so the whole thread
   * fits in one response. An optional status filter is applied in memory over that small set.
   */
  private ProposalListView listForTarget(
      UUID campaignId, ProposalStatus status, ProposalTargetType targetType, UUID targetId) {
    List<ProposalView> proposals =
        proposalRepository.findByTarget(campaignId, targetType, targetId).stream()
            .filter(p -> status == null || p.status() == status)
            .map(ProposalView::from)
            .toList();
    return new ProposalListView(proposals, proposals.size(), 0, proposals.size());
  }
}
