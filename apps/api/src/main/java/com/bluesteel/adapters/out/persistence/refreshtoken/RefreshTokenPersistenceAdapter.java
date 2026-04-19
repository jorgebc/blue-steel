package com.bluesteel.adapters.out.persistence.refreshtoken;

import com.bluesteel.application.port.out.auth.RefreshTokenRepository;
import com.bluesteel.domain.auth.RefreshToken;
import com.bluesteel.domain.auth.RefreshTokenStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** JPA-backed implementation of {@link RefreshTokenRepository}. */
@Component
public class RefreshTokenPersistenceAdapter implements RefreshTokenRepository {

  private final RefreshTokenJpaRepository jpaRepository;

  public RefreshTokenPersistenceAdapter(@Lazy RefreshTokenJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public void save(RefreshToken token) {
    jpaRepository.save(toEntity(token));
  }

  @Override
  public Optional<RefreshToken> findByTokenHash(String tokenHash) {
    return jpaRepository.findByTokenHash(tokenHash).map(this::toDomain);
  }

  @Override
  public void revokeFamily(UUID familyId) {
    jpaRepository.revokeFamily(familyId, Instant.now());
  }

  private RefreshToken toDomain(RefreshTokenJpaEntity e) {
    RefreshTokenStatus status =
        e.getUsedAt() != null ? RefreshTokenStatus.CONSUMED : RefreshTokenStatus.ACTIVE;
    return RefreshToken.reconstitute(
        e.getId(),
        e.getUserId(),
        e.getFamilyId(),
        e.getTokenHash(),
        status,
        e.getExpiresAt(),
        e.getCreatedAt());
  }

  private RefreshTokenJpaEntity toEntity(RefreshToken t) {
    Instant usedAt = t.status() != RefreshTokenStatus.ACTIVE ? Instant.now() : null;
    return new RefreshTokenJpaEntity(
        t.id(), t.userId(), t.tokenHash(), t.familyId(), t.expiresAt(), usedAt, t.createdAt());
  }
}
