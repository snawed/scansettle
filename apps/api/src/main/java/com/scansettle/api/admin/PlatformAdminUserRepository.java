package com.scansettle.api.admin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlatformAdminUserRepository extends JpaRepository<PlatformAdminUser, UUID> {

    Optional<PlatformAdminUser> findByEmail(String email);
}
