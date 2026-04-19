package com.bluesteel.adapters.out.persistence.user;

import com.bluesteel.application.port.out.user.UserRepository;
import com.bluesteel.domain.user.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** JPA-backed implementation of {@link UserRepository}. */
@Component
public class UserPersistenceAdapter implements UserRepository {

  private final UserJpaRepository jpaRepository;

  public UserPersistenceAdapter(@Lazy UserJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public Optional<User> findById(UUID id) {
    return jpaRepository.findById(id).map(this::toDomain);
  }

  @Override
  public Optional<User> findByEmail(String email) {
    return jpaRepository.findByEmail(email).map(this::toDomain);
  }

  @Override
  public boolean existsByIsAdminTrue() {
    return jpaRepository.existsByIsAdminTrue();
  }

  @Override
  public void save(User user) {
    jpaRepository.save(toEntity(user));
  }

  private User toDomain(UserJpaEntity e) {
    return User.create(
        e.getId(),
        e.getEmail(),
        e.getPasswordHash(),
        e.isAdmin(),
        e.isForcePasswordChange(),
        e.getCreatedAt());
  }

  private UserJpaEntity toEntity(User u) {
    return new UserJpaEntity(
        u.id(), u.email(), u.passwordHash(), u.isAdmin(), u.forcePasswordChange(), u.createdAt());
  }
}
