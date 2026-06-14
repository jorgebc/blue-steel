package com.bluesteel.application.service.proposal;

import com.bluesteel.application.event.SessionCommittedEvent;
import com.bluesteel.application.model.proposal.DecideProposalCommand;
import com.bluesteel.application.model.proposal.ProposalDecisionResult;
import com.bluesteel.application.model.proposal.ProposalDecisionType;
import com.bluesteel.application.model.worldstate.CommittedEntityVersion;
import com.bluesteel.application.model.worldstate.EntityWriteCommand;
import com.bluesteel.application.port.in.proposal.DecideProposalUseCase;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.proposal.ProposalRepository;
import com.bluesteel.application.port.out.session.SessionRepository;
import com.bluesteel.application.port.out.worldstate.WorldStatePort;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.exception.InvalidProposalStateTransitionException;
import com.bluesteel.domain.exception.ProposalNotFoundException;
import com.bluesteel.domain.exception.UnauthorizedException;
import com.bluesteel.domain.proposal.Proposal;
import com.bluesteel.domain.proposal.ProposalStatus;
import com.bluesteel.domain.proposal.ProposalVote;
import com.bluesteel.domain.proposal.VoteKind;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GM-only decision on a cosigned proposal (D-018). Approval writes the effective delta — the
 * GM-edited delta if present, else the author's (D-110) — as a new entity version through the
 * existing world-state write path, stamped with the campaign's latest committed session id (D-107),
 * and publishes {@link SessionCommittedEvent} so the new version is embedded (D-063). Veto rejects
 * unilaterally without touching world state. Both record the GM's vote.
 */
@Service
public class ProposalDecisionService implements DecideProposalUseCase {

  private static final Logger log = LoggerFactory.getLogger(ProposalDecisionService.class);
  private static final TypeReference<Map<String, Object>> DELTA_TYPE = new TypeReference<>() {};

  private final CampaignMembershipPort membershipPort;
  private final ProposalRepository proposalRepository;
  private final SessionRepository sessionRepository;
  private final WorldStatePort worldStatePort;
  private final ProposalDeltaMapper deltaMapper;
  private final ObjectMapper objectMapper;
  private final ApplicationEventPublisher eventPublisher;

  public ProposalDecisionService(
      CampaignMembershipPort membershipPort,
      ProposalRepository proposalRepository,
      SessionRepository sessionRepository,
      WorldStatePort worldStatePort,
      ProposalDeltaMapper deltaMapper,
      ObjectMapper objectMapper,
      ApplicationEventPublisher eventPublisher) {
    this.membershipPort = membershipPort;
    this.proposalRepository = proposalRepository;
    this.sessionRepository = sessionRepository;
    this.worldStatePort = worldStatePort;
    this.deltaMapper = deltaMapper;
    this.objectMapper = objectMapper;
    this.eventPublisher = eventPublisher;
  }

  @Override
  @Transactional
  public ProposalDecisionResult decide(DecideProposalCommand command) {
    log.info(
        "Deciding proposal proposalId={} campaignId={} callerId={} decision={}",
        command.proposalId(),
        command.campaignId(),
        command.callerId(),
        command.decision());

    CampaignRole role =
        membershipPort
            .resolveRole(command.campaignId(), command.callerId())
            .orElseThrow(
                () -> new UnauthorizedException("Caller is not a member of this campaign"));
    if (role != CampaignRole.GM) {
      throw new UnauthorizedException("Only the GM may decide a proposal");
    }

    Proposal proposal =
        proposalRepository
            .findById(command.proposalId())
            .filter(p -> p.campaignId().equals(command.campaignId()))
            .orElseThrow(
                () ->
                    new ProposalNotFoundException(
                        "Proposal " + command.proposalId() + " does not exist in this campaign"));

    // Precondition checked before any world-state write so an approve of a non-cosigned proposal
    // never produces a (rolled-back) entity version; the domain transitions guard as defence in
    // depth (D-017, D-018).
    if (proposal.status() != ProposalStatus.COSIGNED) {
      throw new InvalidProposalStateTransitionException(
          "decide requires COSIGNED but was " + proposal.status());
    }

    return command.decision() == ProposalDecisionType.APPROVE
        ? approve(command, proposal)
        : veto(command, proposal);
  }

  private ProposalDecisionResult approve(DecideProposalCommand command, Proposal proposal) {
    Map<String, Object> effectiveDelta =
        command.editedDelta() != null && !command.editedDelta().isEmpty()
            ? command.editedDelta()
            : parseDelta(proposal.proposedDelta());

    UUID latestCommittedSessionId =
        sessionRepository
            .findLatestCommittedSessionId(proposal.campaignId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No committed session to stamp the approved version for campaign "
                            + proposal.campaignId()));

    EntityWriteCommand writeCommand =
        deltaMapper.toWriteCommand(proposal, effectiveDelta, latestCommittedSessionId);
    CommittedEntityVersion version = worldStatePort.writeEntity(writeCommand);

    proposal.approve(version.entityVersionId());
    proposalRepository.save(proposal);
    proposalRepository.saveVote(
        ProposalVote.create(
            UUID.randomUUID(), proposal.id(), command.callerId(), VoteKind.APPROVE, Instant.now()));

    // Embed the new version via the existing async listener (D-063), attributing it to the latest
    // committed session so version_number↔session ordering stays monotonic (D-107).
    eventPublisher.publishEvent(
        new SessionCommittedEvent(
            latestCommittedSessionId, proposal.campaignId(), List.of(version)));

    log.info(
        "Proposal approved proposalId={} resultingEntityVersionId={}",
        proposal.id(),
        version.entityVersionId());
    return new ProposalDecisionResult(version.entityVersionId());
  }

  private ProposalDecisionResult veto(DecideProposalCommand command, Proposal proposal) {
    proposal.reject();
    proposalRepository.save(proposal);
    proposalRepository.saveVote(
        ProposalVote.create(
            UUID.randomUUID(), proposal.id(), command.callerId(), VoteKind.REJECT, Instant.now()));

    log.info("Proposal vetoed proposalId={}", proposal.id());
    return new ProposalDecisionResult(null);
  }

  private Map<String, Object> parseDelta(String json) {
    try {
      return objectMapper.readValue(json, DELTA_TYPE);
    } catch (Exception e) {
      throw new IllegalStateException("Stored proposed_delta is not valid JSON", e);
    }
  }
}
