package com.bluesteel.adapters.out.persistence.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {

  Optional<UserJpaEntity> findByEmail(String email);

  List<UserJpaEntity> findTop10ByEmailContainingIgnoreCaseOrderByEmailAsc(String emailFragment);

  boolean existsByIsAdminTrue();
}
