package com.bluesteel.application.service.session;

import com.bluesteel.application.model.session.SessionListView;
import com.bluesteel.application.model.session.SessionSummaryView;
import com.bluesteel.application.port.in.session.ListSessionsUseCase;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.session.SessionRepository;
import com.bluesteel.domain.exception.UnauthorizedException;
import com.bluesteel.domain.session.Session;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Lists a campaign's sessions (offset-paginated) for any campaign member. */
@Service
public class ListSessionsService implements ListSessionsUseCase {

  private static final Logger log = LoggerFactory.getLogger(ListSessionsService.class);
  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;

  private final CampaignMembershipPort membershipPort;
  private final SessionRepository sessionRepository;

  public ListSessionsService(
      CampaignMembershipPort membershipPort, SessionRepository sessionRepository) {
    this.membershipPort = membershipPort;
    this.sessionRepository = sessionRepository;
  }

  @Override
  public SessionListView list(UUID campaignId, UUID callerId, int page, int size) {
    log.info("Listing sessions campaignId={} callerId={}", campaignId, callerId);

    membershipPort
        .resolveRole(campaignId, callerId)
        .orElseThrow(() -> new UnauthorizedException("Caller is not a member of this campaign"));

    int safePage = Math.max(page, 0);
    int safeSize = size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

    List<SessionSummaryView> sessions =
        sessionRepository.findByCampaignId(campaignId, safePage, safeSize).stream()
            .map(ListSessionsService::toSummary)
            .toList();
    long totalCount = sessionRepository.countByCampaignId(campaignId);

    return new SessionListView(sessions, totalCount, safePage, safeSize);
  }

  private static SessionSummaryView toSummary(Session s) {
    return new SessionSummaryView(
        s.id(), s.status(), s.sequenceNumber(), s.committedAt(), s.createdAt());
  }
}
