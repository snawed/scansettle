package com.scansettle.api.openbanking;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {

    Optional<WebhookEvent> findByProviderAndProviderEventId(String provider, String providerEventId);

    Page<WebhookEvent> findAllByOrderByReceivedAtDesc(Pageable pageable);

    List<WebhookEvent> findByProviderReferenceOrderByReceivedAtDesc(String providerReference);
}
