package com.bluesteel.application.service.session;

import com.bluesteel.application.event.SessionSubmittedEvent;
import com.bluesteel.application.model.session.SubmitSessionCommand;
import com.bluesteel.application.model.session.SubmitSessionResult;
import com.bluesteel.application.port.in.session.SubmitSessionUseCase;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.session.NarrativeBlockRepository;
import com.bluesteel.application.port.out.session.SessionRepository;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.exception.ActiveSessionExistsException;
import com.bluesteel.domain.exception.SummaryTooLargeException;
import com.bluesteel.domain.exception.UnauthorizedException;
import com.bluesteel.domain.session.NarrativeBlock;
import com.bluesteel.domain.session.Session;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Validates and persists a new session submission, then fires the ingestion pipeline. */
@Service
public class SubmitSessionService implements SubmitSessionUseCase {

  private static final Logger log = LoggerFactory.getLogger(SubmitSessionService.class);

  private final CampaignMembershipPort membershipPort;
  private final SessionRepository sessionRepository;
  private final NarrativeBlockRepository narrativeBlockRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final int maxTokens;

  public SubmitSessionService(
      CampaignMembershipPort membershipPort,
      SessionRepository sessionRepository,
      NarrativeBlockRepository narrativeBlockRepository,
      ApplicationEventPublisher eventPublisher,
      @Value("${blue-steel.ingestion.max-tokens:8000}") int maxTokens) {
    this.membershipPort = membershipPort;
    this.sessionRepository = sessionRepository;
    this.narrativeBlockRepository = narrativeBlockRepository;
    this.eventPublisher = eventPublisher;
    this.maxTokens = maxTokens;
  }

  @Override
  @Transactional
  public SubmitSessionResult submit(SubmitSessionCommand command) {
    log.info(
        "Submitting session campaignId={} callerId={}", command.campaignId(), command.callerId());

    CampaignRole role =
        membershipPort
            .resolveRole(command.campaignId(), command.callerId())
            .orElseThrow(
                () -> new UnauthorizedException("Caller is not a member of this campaign"));

    if (role == CampaignRole.PLAYER) {
      throw new UnauthorizedException("Only GMs and editors may submit sessions");
    }

    int estimatedTokens = (int) Math.ceil(command.summaryText().length() / 4.0);
    if (estimatedTokens > maxTokens) {
      throw new SummaryTooLargeException(
          "Summary exceeds the token budget of "
              + maxTokens
              + " (estimated "
              + estimatedTokens
              + " tokens)");
    }

    sessionRepository
        .findActiveByCampaignId(command.campaignId())
        .ifPresent(
            existing -> {
              throw new ActiveSessionExistsException(existing.id());
            });

    UUID sessionId = UUID.randomUUID();
    UUID blockId = UUID.randomUUID();
    Instant now = Instant.now();

    NarrativeBlock block =
        NarrativeBlock.create(blockId, sessionId, command.summaryText(), estimatedTokens, now);
    narrativeBlockRepository.save(block);

    Session session = Session.create(sessionId, command.campaignId(), command.callerId(), now);
    try {
      sessionRepository.save(session);
    } catch (DataIntegrityViolationException e) {
      throw new ActiveSessionExistsException(null);
    }

    eventPublisher.publishEvent(new SessionSubmittedEvent(sessionId, command.campaignId()));

    log.info("Session submitted sessionId={} campaignId={}", sessionId, command.campaignId());

    return new SubmitSessionResult(sessionId, session.status());
  }
}
