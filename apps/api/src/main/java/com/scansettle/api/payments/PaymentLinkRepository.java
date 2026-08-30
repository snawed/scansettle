package com.scansettle.api.payments;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentLinkRepository extends JpaRepository<PaymentLink, UUID> {

    Optional<PaymentLink> findByIdAndMerchantId(UUID id, UUID merchantId);

    Page<PaymentLink> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId, Pageable pageable);
}
