package com.bluesteel.adapters.out.persistence.session;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SessionJpaRepository extends JpaRepository<SessionJpaEntity, UUID> {

  /** Returns the single session in {@code processing} or {@code draft} status for a campaign. */
  @Query(
      "SELECT s FROM SessionJpaEntity s WHERE s.campaignId = :campaignId"
          + " AND s.status IN ('processing', 'draft')")
  Optional<SessionJpaEntity> findActiveByCampaignId(@Param("campaignId") UUID campaignId);

  /** Returns one page of the campaign's sessions; ordering is supplied via {@link Pageable}. */
  List<SessionJpaEntity> findByCampaignId(UUID campaignId, Pageable pageable);

  /** Returns the total number of sessions in the campaign. */
  long countByCampaignId(UUID campaignId);

  /** Returns {@code COALESCE(MAX(sequenceNumber), 0) + 1} across committed sessions (D-069). */
  @Query(
      "SELECT COALESCE(MAX(s.sequenceNumber), 0) + 1 FROM SessionJpaEntity s"
          + " WHERE s.campaignId = ?1 AND s.status = 'committed'")
  int nextSequenceNumber(UUID campaignId);

  /** Returns the id of the committed session with the highest sequence number (D-107). */
  @Query(
      "SELECT s.id FROM SessionJpaEntity s WHERE s.campaignId = ?1 AND s.status = 'committed'"
          + " AND s.sequenceNumber = (SELECT MAX(s2.sequenceNumber) FROM SessionJpaEntity s2"
          + " WHERE s2.campaignId = ?1 AND s2.status = 'committed')")
  Optional<UUID> findLatestCommittedSessionId(UUID campaignId);
}
