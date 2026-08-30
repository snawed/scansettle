package com.scansettle.api.payments;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProviderTransactionRepository extends JpaRepository<ProviderTransaction, UUID> {

    Optional<ProviderTransaction> findByPaymentId(UUID paymentId);

    Optional<ProviderTransaction> findByProviderReference(String providerReference);
}
