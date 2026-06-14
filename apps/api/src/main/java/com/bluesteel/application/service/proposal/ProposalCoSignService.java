package com.bluesteel.application.service.proposal;

import com.bluesteel.application.model.proposal.CoSignProposalCommand;
import com.bluesteel.application.model.proposal.ProposalView;
import com.bluesteel.application.port.in.proposal.CoSignProposalUseCase;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.proposal.ProposalRepository;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.exception.AuthorCannotCoSignException;
import com.bluesteel.domain.exception.DuplicateVoteException;
import com.bluesteel.domain.exception.GmCannotCoSignException;
import com.bluesteel.domain.exception.ProposalNotFoundException;
import com.bluesteel.domain.exception.UnauthorizedException;
import com.bluesteel.domain.proposal.Proposal;
import com.bluesteel.domain.proposal.ProposalVote;
import com.bluesteel.domain.proposal.VoteKind;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies a co-sign vote to a proposal: any non-author member may co-sign once, and the first
 * co-sign transitions the proposal {@code OPEN → COSIGNED} (D-017, D-109).
 */
@Service
public class ProposalCoSignService implements CoSignProposalUseCase {

  private static final Logger log = LoggerFactory.getLogger(ProposalCoSignService.class);

  private final CampaignMembershipPort membershipPort;
  private final ProposalRepository proposalRepository;

  public ProposalCoSignService(
      CampaignMembershipPort membershipPort, ProposalRepository proposalRepository) {
    this.membershipPort = membershipPort;
    this.proposalRepository = proposalRepository;
  }

  @Override
  @Transactional
  public ProposalView coSign(CoSignProposalCommand command) {
    log.info(
        "Co-signing proposal proposalId={} campaignId={} callerId={}",
        command.proposalId(),
        command.campaignId(),
        command.callerId());

    CampaignRole role =
        membershipPort
            .resolveRole(command.campaignId(), command.callerId())
            .orElseThrow(
                () -> new UnauthorizedException("Caller is not a member of this campaign"));
    // The GM is the decider, not an endorser (D-017). Letting the GM co-sign would also consume
    // their one vote slot and collide with the later approve/veto vote (D-109).
    if (role == CampaignRole.GM) {
      throw new GmCannotCoSignException("The GM decides proposals and cannot co-sign them");
    }

    Proposal proposal =
        proposalRepository
            .findById(command.proposalId())
            .filter(p -> p.campaignId().equals(command.campaignId()))
            .orElseThrow(
                () ->
                    new ProposalNotFoundException(
                        "Proposal " + command.proposalId() + " does not exist in this campaign"));

    if (proposal.ownerId().equals(command.callerId())) {
      throw new AuthorCannotCoSignException("The author cannot co-sign their own proposal");
    }

    try {
      proposalRepository.saveVote(
          ProposalVote.create(
              UUID.randomUUID(),
              proposal.id(),
              command.callerId(),
              VoteKind.COSIGN,
              Instant.now()));
    } catch (DataIntegrityViolationException e) {
      throw new DuplicateVoteException("You have already voted on this proposal");
    }

    proposal.coSign();
    proposalRepository.save(proposal);

    log.info("Proposal co-signed proposalId={} status={}", proposal.id(), proposal.status());
    return ProposalView.from(proposal);
  }
}
