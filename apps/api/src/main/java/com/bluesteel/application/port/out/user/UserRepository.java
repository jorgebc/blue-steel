package com.bluesteel.application.port.out.user;

import com.bluesteel.domain.user.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence-layer contract for {@link User} aggregates. */
public interface UserRepository {

  Optional<User> findById(UUID id);

  Optional<User> findByEmail(String email);

  /** Returns users whose email contains the fragment (case-insensitive), capped for the picker. */
  List<User> searchByEmail(String emailFragment);

  boolean existsByIsAdminTrue();

  void save(User user);
}
