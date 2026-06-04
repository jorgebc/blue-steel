package com.bluesteel.application.service.worldstate;

import com.bluesteel.application.model.worldstate.TimelineFilter;
import com.bluesteel.application.model.worldstate.TimelinePage;
import com.bluesteel.application.port.in.worldstate.GetTimelineUseCase;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.worldstate.TimelineReadPort;
import com.bluesteel.domain.exception.UnauthorizedException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Member-authorized access to the Timeline feed (D-010, D-043). Authorizes the caller as a campaign
 * member on every call, clamps the page limit, and delegates to the read port.
 */
@Service
public class TimelineService implements GetTimelineUseCase {

  private static final Logger log = LoggerFactory.getLogger(TimelineService.class);
  private static final int DEFAULT_LIMIT = 20;
  private static final int MAX_LIMIT = 50;

  private final CampaignMembershipPort membershipPort;
  private final TimelineReadPort timelineReadPort;

  public TimelineService(CampaignMembershipPort membershipPort, TimelineReadPort timelineReadPort) {
    this.membershipPort = membershipPort;
    this.timelineReadPort = timelineReadPort;
  }

  @Override
  public TimelinePage getTimeline(
      UUID campaignId, UUID callerId, String cursor, int limit, TimelineFilter filter) {
    log.info("Reading timeline campaignId={} callerId={}", campaignId, callerId);
    requireMember(campaignId, callerId);

    int safeLimit = limit < 1 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
    TimelineFilter safeFilter = filter == null ? TimelineFilter.none() : filter;

    return timelineReadPort.page(campaignId, cursor, safeLimit, safeFilter);
  }

  private void requireMember(UUID campaignId, UUID callerId) {
    membershipPort
        .resolveRole(campaignId, callerId)
        .orElseThrow(() -> new UnauthorizedException("Caller is not a member of this campaign"));
  }
}
