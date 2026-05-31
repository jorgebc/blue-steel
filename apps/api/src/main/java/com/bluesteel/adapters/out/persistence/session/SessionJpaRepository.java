package com.bluesteel.adapters.out.persistence.session;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SessionJpaRepository extends JpaRepository<SessionJpaEntity, UUID> {

  /** Returns the single session in {@code processing} or {@code draft} status for a campaign. */
  @Query(
      "SELECT s FROM SessionJpaEntity s WHERE s.campaignId = :campaignId"
          + " AND s.status IN ('processing', 'draft')")
  Optional<SessionJpaEntity> findActiveByCampaignId(@Param("campaignId") UUID campaignId);

  /** Returns {@code COALESCE(MAX(sequenceNumber), 0) + 1} across committed sessions (D-069). */
  @Query(
      "SELECT COALESCE(MAX(s.sequenceNumber), 0) + 1 FROM SessionJpaEntity s"
          + " WHERE s.campaignId = ?1 AND s.status = 'committed'")
  int nextSequenceNumber(UUID campaignId);
}
