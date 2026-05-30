package com.bluesteel.application.service.session;

import com.bluesteel.application.port.in.session.DiscardSessionUseCase;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.session.SessionRepository;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.exception.SessionNotFoundException;
import com.bluesteel.domain.exception.UnauthorizedException;
import com.bluesteel.domain.session.Session;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Discards a draft session; restricted to GMs. */
@Service
public class DiscardSessionService implements DiscardSessionUseCase {

  private static final Logger log = LoggerFactory.getLogger(DiscardSessionService.class);

  private final CampaignMembershipPort membershipPort;
  private final SessionRepository sessionRepository;

  public DiscardSessionService(
      CampaignMembershipPort membershipPort, SessionRepository sessionRepository) {
    this.membershipPort = membershipPort;
    this.sessionRepository = sessionRepository;
  }

  @Override
  @Transactional
  public void discard(UUID sessionId, UUID callerId, UUID campaignId) {
    log.info("Discarding session sessionId={} callerId={}", sessionId, callerId);

    CampaignRole role =
        membershipPort
            .resolveRole(campaignId, callerId)
            .orElseThrow(
                () -> new UnauthorizedException("Caller is not a member of this campaign"));

    if (role != CampaignRole.GM) {
      throw new UnauthorizedException("Only GMs may discard sessions");
    }

    Session session =
        sessionRepository
            .findById(sessionId)
            .orElseThrow(() -> new SessionNotFoundException("Session not found: " + sessionId));

    session.discard();
    sessionRepository.save(session);

    log.info("Session discarded sessionId={}", sessionId);
  }
}
