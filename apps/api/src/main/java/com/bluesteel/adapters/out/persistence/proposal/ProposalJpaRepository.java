package com.bluesteel.adapters.out.persistence.proposal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ProposalJpaRepository extends JpaRepository<ProposalJpaEntity, UUID> {

  /** Returns one page of the campaign's proposals; a null {@code status} matches all statuses. */
  @Query(
      "SELECT p FROM ProposalJpaEntity p WHERE p.campaignId = :campaignId"
          + " AND (:status IS NULL OR p.status = :status)")
  List<ProposalJpaEntity> findByCampaign(
      @Param("campaignId") UUID campaignId, @Param("status") String status, Pageable pageable);

  /** Counts the campaign's proposals; a null {@code status} matches all statuses. */
  @Query(
      "SELECT COUNT(p) FROM ProposalJpaEntity p WHERE p.campaignId = :campaignId"
          + " AND (:status IS NULL OR p.status = :status)")
  long countByCampaign(@Param("campaignId") UUID campaignId, @Param("status") String status);

  /** Returns all proposals targeting the given entity; ordering supplied via {@link Sort}. */
  @Query(
      "SELECT p FROM ProposalJpaEntity p WHERE p.campaignId = :campaignId"
          + " AND p.targetEntityType = :targetType AND p.targetEntityId = :targetId")
  List<ProposalJpaEntity> findByTarget(
      @Param("campaignId") UUID campaignId,
      @Param("targetType") String targetType,
      @Param("targetId") UUID targetId,
      Sort sort);

  /** Returns true when an open or cosigned proposal exists for the target entity (D-106). */
  @Query(
      "SELECT COUNT(p) > 0 FROM ProposalJpaEntity p WHERE p.campaignId = :campaignId"
          + " AND p.targetEntityType = :targetType AND p.targetEntityId = :targetId"
          + " AND p.status IN ('open', 'cosigned')")
  boolean existsOpenForTarget(
      @Param("campaignId") UUID campaignId,
      @Param("targetType") String targetType,
      @Param("targetId") UUID targetId);
}
