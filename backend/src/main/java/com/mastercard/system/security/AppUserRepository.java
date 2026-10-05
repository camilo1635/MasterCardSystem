package com.mastercard.system.security;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);

    long countByRoleAndActiveTrue(Role role);

    List<AppUser> findAllByOrderByUsernameAsc();
}
