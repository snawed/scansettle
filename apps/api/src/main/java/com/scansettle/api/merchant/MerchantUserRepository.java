package com.scansettle.api.merchant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MerchantUserRepository extends JpaRepository<MerchantUser, UUID> {

    Optional<MerchantUser> findByEmail(String email);

    boolean existsByEmail(String email);

    List<MerchantUser> findByMerchantId(UUID merchantId);

    Optional<MerchantUser> findByIdAndMerchantId(UUID id, UUID merchantId);
}
