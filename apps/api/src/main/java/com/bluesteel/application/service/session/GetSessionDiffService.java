package com.bluesteel.application.service.session;

import com.bluesteel.application.model.session.DiffPayload;
import com.bluesteel.application.port.in.session.GetSessionDiffUseCase;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.session.SessionRepository;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.exception.SessionNotFoundException;
import com.bluesteel.domain.exception.UnauthorizedException;
import com.bluesteel.domain.session.Session;
import com.bluesteel.domain.session.SessionStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Returns the diff payload for a session in DRAFT status; restricted to GMs and Editors. */
@Service
public class GetSessionDiffService implements GetSessionDiffUseCase {

  private static final Logger log = LoggerFactory.getLogger(GetSessionDiffService.class);

  private final CampaignMembershipPort membershipPort;
  private final SessionRepository sessionRepository;
  private final ObjectMapper objectMapper;

  public GetSessionDiffService(
      CampaignMembershipPort membershipPort,
      SessionRepository sessionRepository,
      ObjectMapper objectMapper) {
    this.membershipPort = membershipPort;
    this.sessionRepository = sessionRepository;
    this.objectMapper = objectMapper;
  }

  @Override
  public DiffPayload getDiff(UUID callerId, UUID campaignId, UUID sessionId) {
    log.info("Getting session diff sessionId={} callerId={}", sessionId, callerId);

    CampaignRole role =
        membershipPort
            .resolveRole(campaignId, callerId)
            .orElseThrow(
                () -> new UnauthorizedException("Caller is not a member of this campaign"));

    if (role != CampaignRole.GM && role != CampaignRole.EDITOR) {
      throw new UnauthorizedException("Only GMs and Editors may view session diffs");
    }

    Session session =
        sessionRepository
            .findById(sessionId)
            .filter(s -> s.campaignId().equals(campaignId))
            .orElseThrow(() -> new SessionNotFoundException("Session not found: " + sessionId));

    if (session.status() != SessionStatus.DRAFT) {
      throw new SessionNotFoundException(
          "Diff not available — session is not in DRAFT status: " + sessionId);
    }

    try {
      DiffPayload payload = objectMapper.readValue(session.diffPayload(), DiffPayload.class);
      log.info("Session diff retrieved sessionId={}", sessionId);
      return payload;
    } catch (Exception e) {
      throw new RuntimeException("Failed to deserialize diff payload for session " + sessionId, e);
    }
  }
}
