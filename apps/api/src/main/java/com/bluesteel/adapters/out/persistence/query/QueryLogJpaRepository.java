package com.bluesteel.adapters.out.persistence.query;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface QueryLogJpaRepository extends JpaRepository<QueryLogJpaEntity, UUID> {

  /** Returns one page of the campaign's entries, newest first, honouring exact offset/limit. */
  @Query(
      value =
          "SELECT * FROM query_log WHERE campaign_id = :campaignId"
              + " ORDER BY created_at DESC OFFSET :offset LIMIT :limit",
      nativeQuery = true)
  List<QueryLogJpaEntity> findPage(
      @Param("campaignId") UUID campaignId, @Param("offset") int offset, @Param("limit") int limit);

  long countByCampaignId(UUID campaignId);
}
