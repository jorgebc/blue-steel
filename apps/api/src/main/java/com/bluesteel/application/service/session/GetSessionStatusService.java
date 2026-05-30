package com.bluesteel.application.service.session;

import com.bluesteel.application.model.session.SessionStatusView;
import com.bluesteel.application.port.in.session.GetSessionStatusUseCase;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.session.SessionRepository;
import com.bluesteel.domain.exception.SessionNotFoundException;
import com.bluesteel.domain.exception.UnauthorizedException;
import com.bluesteel.domain.session.Session;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Returns the current status of a session to any campaign member. */
@Service
public class GetSessionStatusService implements GetSessionStatusUseCase {

  private static final Logger log = LoggerFactory.getLogger(GetSessionStatusService.class);

  private final CampaignMembershipPort membershipPort;
  private final SessionRepository sessionRepository;

  public GetSessionStatusService(
      CampaignMembershipPort membershipPort, SessionRepository sessionRepository) {
    this.membershipPort = membershipPort;
    this.sessionRepository = sessionRepository;
  }

  @Override
  public SessionStatusView getStatus(UUID sessionId, UUID callerId, UUID campaignId) {
    log.info("Getting session status sessionId={} callerId={}", sessionId, callerId);

    membershipPort
        .resolveRole(campaignId, callerId)
        .orElseThrow(() -> new UnauthorizedException("Caller is not a member of this campaign"));

    Session session =
        sessionRepository
            .findById(sessionId)
            .orElseThrow(() -> new SessionNotFoundException("Session not found: " + sessionId));

    return new SessionStatusView(session.id(), session.status(), session.failureReason(), null);
  }
}
