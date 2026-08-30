package com.scansettle.api.merchant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MerchantBankAccountRepository extends JpaRepository<MerchantBankAccount, UUID> {

    Optional<MerchantBankAccount> findFirstByMerchantIdAndStatusOrderByCreatedAtDesc(
            UUID merchantId, MerchantBankAccount.Status status);
}
