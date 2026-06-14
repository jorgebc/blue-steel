package com.bluesteel.application.service.proposal;

import com.bluesteel.application.model.proposal.CreateProposalCommand;
import com.bluesteel.application.model.proposal.ProposalView;
import com.bluesteel.application.port.in.proposal.CreateProposalUseCase;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.proposal.ProposalRepository;
import com.bluesteel.application.port.out.session.SessionRepository;
import com.bluesteel.application.port.out.worldstate.WorldStatePort;
import com.bluesteel.domain.exception.ConcurrentProposalException;
import com.bluesteel.domain.exception.EmptyDeltaException;
import com.bluesteel.domain.exception.ProposalTargetNotFoundException;
import com.bluesteel.domain.exception.SessionNotFoundException;
import com.bluesteel.domain.exception.UnauthorizedException;
import com.bluesteel.domain.exception.UnsupportedTargetTypeException;
import com.bluesteel.domain.proposal.Proposal;
import com.bluesteel.domain.proposal.ProposalTargetType;
import com.bluesteel.domain.session.Session;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Submits a new proposal after validating membership, the target type ({@code actor}/{@code space}
 * only, D-108), the target's existence, the provenance session, and the delta; stamps {@code
 * expires_at} from the configured TTL (D-105) and blocks concurrent proposals on the same target
 * (D-106).
 */
@Service
public class ProposalCreationService implements CreateProposalUseCase {

  private static final Logger log = LoggerFactory.getLogger(ProposalCreationService.class);

  private final CampaignMembershipPort membershipPort;
  private final ProposalRepository proposalRepository;
  private final WorldStatePort worldStatePort;
  private final SessionRepository sessionRepository;
  private final ObjectMapper objectMapper;
  private final int ttlDays;

  public ProposalCreationService(
      CampaignMembershipPort membershipPort,
      ProposalRepository proposalRepository,
      WorldStatePort worldStatePort,
      SessionRepository sessionRepository,
      ObjectMapper objectMapper,
      @Value("${blue-steel.proposal.ttl-days:30}") int ttlDays) {
    this.membershipPort = membershipPort;
    this.proposalRepository = proposalRepository;
    this.worldStatePort = worldStatePort;
    this.sessionRepository = sessionRepository;
    this.objectMapper = objectMapper;
    this.ttlDays = ttlDays;
  }

  @Override
  @Transactional
  public ProposalView create(CreateProposalCommand command) {
    log.info(
        "Creating proposal campaignId={} callerId={} targetType={}",
        command.campaignId(),
        command.callerId(),
        command.targetType());

    membershipPort
        .resolveRole(command.campaignId(), command.callerId())
        .orElseThrow(() -> new UnauthorizedException("Caller is not a member of this campaign"));

    ProposalTargetType targetType = parseTargetType(command.targetType());

    if (command.targetId() == null) {
      throw new EmptyDeltaException("targetId must not be null");
    }
    if (command.proposedDelta() == null || command.proposedDelta().isEmpty()) {
      throw new EmptyDeltaException("proposedDelta must not be empty");
    }

    String entityType = targetType.name().toLowerCase(Locale.ROOT);
    if (!worldStatePort.existsInCampaign(entityType, command.targetId(), command.campaignId())) {
      throw new ProposalTargetNotFoundException(
          "Target " + entityType + " " + command.targetId() + " does not exist in this campaign");
    }

    Session provenanceSession =
        sessionRepository
            .findById(command.sessionId())
            .filter(s -> s.campaignId().equals(command.campaignId()))
            .orElseThrow(
                () ->
                    new SessionNotFoundException(
                        "Provenance session "
                            + command.sessionId()
                            + " does not exist in this campaign"));

    if (proposalRepository.existsOpenForTarget(
        command.campaignId(), targetType, command.targetId())) {
      throw new ConcurrentProposalException(
          "An open or cosigned proposal already exists for this target");
    }

    Instant now = Instant.now();
    Proposal proposal =
        Proposal.create(
            UUID.randomUUID(),
            command.campaignId(),
            targetType,
            command.targetId(),
            command.callerId(),
            provenanceSession.id(),
            serializeDelta(command.proposedDelta()),
            now.plus(ttlDays, ChronoUnit.DAYS),
            now);
    proposalRepository.save(proposal);

    log.info("Proposal created proposalId={} campaignId={}", proposal.id(), command.campaignId());
    return ProposalView.from(proposal);
  }

  private static ProposalTargetType parseTargetType(String raw) {
    if (raw == null) {
      throw new UnsupportedTargetTypeException("targetType must be one of: actor, space");
    }
    try {
      return ProposalTargetType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new UnsupportedTargetTypeException(
          "Unsupported target type '" + raw + "'; only actor and space are allowed");
    }
  }

  private String serializeDelta(java.util.Map<String, Object> delta) {
    try {
      return objectMapper.writeValueAsString(delta);
    } catch (JsonProcessingException e) {
      throw new EmptyDeltaException("proposedDelta is not serializable JSON");
    }
  }
}
