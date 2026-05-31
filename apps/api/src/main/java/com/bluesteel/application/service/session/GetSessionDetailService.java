package com.bluesteel.application.service.session;

import com.bluesteel.application.model.session.SessionDetailView;
import com.bluesteel.application.port.in.session.GetSessionDetailUseCase;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.session.NarrativeBlockRepository;
import com.bluesteel.application.port.out.session.SessionRepository;
import com.bluesteel.domain.exception.SessionNotFoundException;
import com.bluesteel.domain.exception.UnauthorizedException;
import com.bluesteel.domain.session.NarrativeBlock;
import com.bluesteel.domain.session.Session;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Returns a single session's detail (with its narrative block reference) to any campaign member.
 */
@Service
public class GetSessionDetailService implements GetSessionDetailUseCase {

  private static final Logger log = LoggerFactory.getLogger(GetSessionDetailService.class);

  private final CampaignMembershipPort membershipPort;
  private final SessionRepository sessionRepository;
  private final NarrativeBlockRepository narrativeBlockRepository;

  public GetSessionDetailService(
      CampaignMembershipPort membershipPort,
      SessionRepository sessionRepository,
      NarrativeBlockRepository narrativeBlockRepository) {
    this.membershipPort = membershipPort;
    this.sessionRepository = sessionRepository;
    this.narrativeBlockRepository = narrativeBlockRepository;
  }

  @Override
  public SessionDetailView getDetail(UUID sessionId, UUID callerId, UUID campaignId) {
    log.info("Getting session detail sessionId={} callerId={}", sessionId, callerId);

    membershipPort
        .resolveRole(campaignId, callerId)
        .orElseThrow(() -> new UnauthorizedException("Caller is not a member of this campaign"));

    Session session =
        sessionRepository
            .findById(sessionId)
            .filter(s -> s.campaignId().equals(campaignId))
            .orElseThrow(() -> new SessionNotFoundException("Session not found: " + sessionId));

    UUID narrativeBlockId =
        narrativeBlockRepository.findBySessionId(sessionId).map(NarrativeBlock::id).orElse(null);

    return new SessionDetailView(
        session.id(),
        session.campaignId(),
        session.status(),
        session.sequenceNumber(),
        session.failureReason(),
        session.committedAt(),
        session.createdAt(),
        session.updatedAt(),
        narrativeBlockId);
  }
}
