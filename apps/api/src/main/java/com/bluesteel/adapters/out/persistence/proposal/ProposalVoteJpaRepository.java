package com.bluesteel.adapters.out.persistence.proposal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProposalVoteJpaRepository extends JpaRepository<ProposalVoteJpaEntity, UUID> {}
