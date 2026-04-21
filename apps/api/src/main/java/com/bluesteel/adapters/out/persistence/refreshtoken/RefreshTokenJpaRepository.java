package com.bluesteel.adapters.out.persistence.refreshtoken;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenJpaEntity, UUID> {

  Optional<RefreshTokenJpaEntity> findByTokenHash(String tokenHash);

  @Modifying
  @Query(
      "UPDATE RefreshTokenJpaEntity t SET t.usedAt = :now WHERE t.familyId = :familyId AND t.usedAt IS NULL")
  void revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);
}
