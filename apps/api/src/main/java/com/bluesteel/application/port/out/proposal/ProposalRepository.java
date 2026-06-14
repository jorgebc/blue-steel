package com.bluesteel.application.port.out.proposal;

import com.bluesteel.application.model.proposal.ProposalFilter;
import com.bluesteel.application.model.proposal.ProposalPage;
import com.bluesteel.domain.proposal.Proposal;
import com.bluesteel.domain.proposal.ProposalTargetType;
import com.bluesteel.domain.proposal.ProposalVote;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Driven port for persisting and querying {@link Proposal} aggregates and their votes. */
public interface ProposalRepository {

  /** Persists a new or updated proposal. */
  void save(Proposal proposal);

  /** Returns the proposal by id, or empty if not found. */
  Optional<Proposal> findById(UUID id);

  /**
   * Returns one offset-paginated page of the campaign's proposals, newest first, optionally
   * narrowed by {@link ProposalFilter#status()}.
   */
  List<Proposal> findByCampaign(UUID campaignId, ProposalFilter filter, ProposalPage page);

  /** Returns all proposals in the campaign targeting the given entity, newest first. */
  List<Proposal> findByTarget(UUID campaignId, ProposalTargetType targetType, UUID targetId);

  /** Returns the total number of the campaign's proposals matching {@code filter}. */
  long countByCampaign(UUID campaignId, ProposalFilter filter);

  /** Persists a single vote on a proposal. */
  void saveVote(ProposalVote vote);

  /**
   * Returns {@code true} when an {@code open} or {@code cosigned} proposal already exists for the
   * target entity in the campaign. Backs the concurrent-proposal block (D-106).
   */
  boolean existsOpenForTarget(UUID campaignId, ProposalTargetType targetType, UUID targetId);
}
