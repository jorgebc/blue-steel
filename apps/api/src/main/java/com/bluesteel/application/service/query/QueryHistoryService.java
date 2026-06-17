package com.bluesteel.application.service.query;

import com.bluesteel.application.model.query.QueryHistoryPage;
import com.bluesteel.application.model.query.QueryHistoryView;
import com.bluesteel.application.model.query.QueryLogEntry;
import com.bluesteel.application.port.in.query.GetQueryHistoryUseCase;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.query.QueryLogRepository;
import com.bluesteel.domain.exception.UnauthorizedException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Reads a campaign's Q&amp;A history (offset-paginated, newest first) for any member (D-058). */
@Service
public class QueryHistoryService implements GetQueryHistoryUseCase {

  private static final Logger log = LoggerFactory.getLogger(QueryHistoryService.class);
  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;

  private final CampaignMembershipPort membershipPort;
  private final QueryLogRepository queryLogRepository;

  public QueryHistoryService(
      CampaignMembershipPort membershipPort, QueryLogRepository queryLogRepository) {
    this.membershipPort = membershipPort;
    this.queryLogRepository = queryLogRepository;
  }

  @Override
  public QueryHistoryView getHistory(UUID campaignId, UUID callerId, int page, int size) {
    log.info("Reading Q&A history campaignId={} callerId={} page={}", campaignId, callerId, page);

    membershipPort
        .resolveRole(campaignId, callerId)
        .orElseThrow(() -> new UnauthorizedException("Caller is not a member of this campaign"));

    int safePage = Math.max(page, 0);
    int safeSize = size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
    QueryHistoryPage paging = new QueryHistoryPage(safePage, safeSize);

    int offset = paging.page() * paging.size();
    List<QueryLogEntry> entries =
        queryLogRepository.findByCampaign(campaignId, offset, paging.size());
    long totalCount = queryLogRepository.countByCampaign(campaignId);

    return new QueryHistoryView(entries, totalCount, paging.page(), paging.size());
  }
}
