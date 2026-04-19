package com.bluesteel.application.port.out.user;

import com.bluesteel.domain.user.User;
import java.util.Optional;
import java.util.UUID;

/** Persistence-layer contract for {@link User} aggregates. */
public interface UserRepository {

  Optional<User> findById(UUID id);

  Optional<User> findByEmail(String email);

  boolean existsByIsAdminTrue();

  void save(User user);
}
