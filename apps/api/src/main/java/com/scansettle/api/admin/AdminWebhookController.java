package com.scansettle.api.admin;

import com.scansettle.api.openbanking.WebhookEvent;
import com.scansettle.api.openbanking.WebhookEventRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** Inbound webhook inspection across every merchant — see docs/security.md. */
@RestController
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class AdminWebhookController {

    private final WebhookEventRepository webhookEventRepository;

    public AdminWebhookController(WebhookEventRepository webhookEventRepository) {
        this.webhookEventRepository = webhookEventRepository;
    }

    public record WebhookEventResponse(String id, String source, String provider, String providerEventId,
                                        String providerReference, boolean signatureValid, String processingResult,
                                        Instant receivedAt, Instant processedAt) {
    }

    @GetMapping("/api/v1/admin/webhooks")
    public List<WebhookEventResponse> list(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "50") int size) {
        return webhookEventRepository.findAllByOrderByReceivedAtDesc(PageRequest.of(page, size))
                .map(this::toResponse).toList();
    }

    private WebhookEventResponse toResponse(WebhookEvent event) {
        return new WebhookEventResponse(event.getId().toString(), event.getSource().name(), event.getProvider(),
                event.getProviderEventId(), event.getProviderReference(), event.isSignatureValid(),
                event.getProcessingResult() != null ? event.getProcessingResult().name() : null,
                event.getReceivedAt(), event.getProcessedAt());
    }
}
