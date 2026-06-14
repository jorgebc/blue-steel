package com.bluesteel.adapters.out.persistence.proposal;

import com.bluesteel.application.model.proposal.ProposalFilter;
import com.bluesteel.application.model.proposal.ProposalPage;
import com.bluesteel.application.port.out.proposal.ProposalRepository;
import com.bluesteel.domain.proposal.Proposal;
import com.bluesteel.domain.proposal.ProposalStatus;
import com.bluesteel.domain.proposal.ProposalTargetType;
import com.bluesteel.domain.proposal.ProposalVote;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/** JPA-backed implementation of {@link ProposalRepository}. */
@Component
public class ProposalPersistenceAdapter implements ProposalRepository {

  private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"));

  private final ProposalJpaRepository proposalRepository;
  private final ProposalVoteJpaRepository voteRepository;

  public ProposalPersistenceAdapter(
      @Lazy ProposalJpaRepository proposalRepository,
      @Lazy ProposalVoteJpaRepository voteRepository) {
    this.proposalRepository = proposalRepository;
    this.voteRepository = voteRepository;
  }

  @Override
  public void save(Proposal proposal) {
    // Flush eagerly so the uidx_proposals_open_target violation (the race-safe backstop for the
    // D-106 concurrent-proposal rule) surfaces as a DataIntegrityViolationException at the call
    // site rather than deferring to transaction commit, past the service's catch boundary — same
    // rationale as saveVote. Proposal writes are low-frequency (create/transition/decide).
    proposalRepository.saveAndFlush(toEntity(proposal));
  }

  @Override
  public Optional<Proposal> findById(UUID id) {
    return proposalRepository.findById(id).map(this::toDomain);
  }

  @Override
  public List<Proposal> findByCampaign(UUID campaignId, ProposalFilter filter, ProposalPage page) {
    String status = filter.status() == null ? null : filter.status().name().toLowerCase();
    return proposalRepository
        .findByCampaign(campaignId, status, PageRequest.of(page.page(), page.size(), NEWEST_FIRST))
        .stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public List<Proposal> findByTarget(
      UUID campaignId, ProposalTargetType targetType, UUID targetId) {
    return proposalRepository
        .findByTarget(campaignId, targetType.name().toLowerCase(), targetId, NEWEST_FIRST)
        .stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public long countByCampaign(UUID campaignId, ProposalFilter filter) {
    String status = filter.status() == null ? null : filter.status().name().toLowerCase();
    return proposalRepository.countByCampaign(campaignId, status);
  }

  @Override
  public void saveVote(ProposalVote vote) {
    // Flush eagerly so the uidx_proposal_votes_proposal_voter violation surfaces as a
    // DataIntegrityViolationException at the call site (app-assigned UUID ids otherwise defer the
    // INSERT to transaction commit, past the service's catch boundary). (D-109)
    voteRepository.saveAndFlush(toEntity(vote));
  }

  @Override
  public boolean existsOpenForTarget(
      UUID campaignId, ProposalTargetType targetType, UUID targetId) {
    return proposalRepository.existsOpenForTarget(
        campaignId, targetType.name().toLowerCase(), targetId);
  }

  private Proposal toDomain(ProposalJpaEntity e) {
    return Proposal.reconstitute(
        e.getId(),
        e.getCampaignId(),
        ProposalTargetType.valueOf(e.getTargetEntityType().toUpperCase()),
        e.getTargetEntityId(),
        e.getAuthorId(),
        e.getSessionId(),
        e.getProposedDelta(),
        ProposalStatus.valueOf(e.getStatus().toUpperCase()),
        e.getExpiresAt(),
        e.getResultingEntityVersionId(),
        e.getCreatedAt());
  }

  private ProposalJpaEntity toEntity(Proposal p) {
    return new ProposalJpaEntity(
        p.id(),
        p.campaignId(),
        p.targetType().name().toLowerCase(),
        p.targetId(),
        p.ownerId(),
        p.proposedDelta(),
        p.status().name().toLowerCase(),
        p.sessionId(),
        p.resultingEntityVersionId(),
        p.expiresAt(),
        p.createdAt());
  }

  private ProposalVoteJpaEntity toEntity(ProposalVote v) {
    return new ProposalVoteJpaEntity(
        v.id(), v.proposalId(), v.voterId(), v.kind().name().toLowerCase(), v.createdAt());
  }
}
