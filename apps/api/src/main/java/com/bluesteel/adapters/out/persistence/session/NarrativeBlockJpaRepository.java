package com.bluesteel.adapters.out.persistence.session;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface NarrativeBlockJpaRepository extends JpaRepository<NarrativeBlockJpaEntity, UUID> {}
