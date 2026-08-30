package com.scansettle.api.openbanking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scansettle.api.audit.AuditEvent;
import com.scansettle.api.audit.AuditService;
import com.scansettle.api.openbanking.model.WebhookProcessingResult;
import com.scansettle.api.openbanking.model.WebhookRequest;
import com.scansettle.api.payments.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * The single place an inbound Open Banking webhook is verified, deduplicated, and
 * applied — signature check, idempotency against {@link WebhookEvent}'s
 * (provider, providerEventId) uniqueness, then {@link PaymentService}. Both the real
 * public endpoint ({@link OpenBankingWebhookController}) and the local mock-bank
 * decision endpoint call this directly rather than one calling the other over HTTP —
 * a self-HTTP loopback adds fragility (wrong port under dynamic test ports, network
 * edge cases) without exercising any additional real code, since the signing and
 * verification both happen in this same process either way.
 */
@Service
public class WebhookIngestionService {

    private static final Logger log = LoggerFactory.getLogger(WebhookIngestionService.class);
    private static final String PROVIDER = "mock";

    private final OpenBankingProvider openBankingProvider;
    private final WebhookEventRepository webhookEventRepository;
    private final PaymentService paymentService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public WebhookIngestionService(OpenBankingProvider openBankingProvider,
                                    WebhookEventRepository webhookEventRepository, PaymentService paymentService,
                                    AuditService auditService, ObjectMapper objectMapper) {
        this.openBankingProvider = openBankingProvider;
        this.webhookEventRepository = webhookEventRepository;
        this.paymentService = paymentService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public enum Outcome {
        PROCESSED, DUPLICATE, REJECTED_INVALID_SIGNATURE, NO_MATCHING_PAYMENT
    }

    @Transactional
    public Outcome ingestOpenBankingWebhook(Map<String, String> headers, String rawBody) {
        WebhookProcessingResult result = openBankingProvider.handleWebhook(new WebhookRequest(headers, rawBody));

        String eventId = result.providerEventId() != null ? result.providerEventId() : UUID.randomUUID().toString();
        String storedPayload = result.providerEventId() != null ? rawBody : wrapUnparseablePayload(rawBody);

        if (webhookEventRepository.findByProviderAndProviderEventId(PROVIDER, eventId).isPresent()) {
            // Same event delivered again — acknowledge without reprocessing
            // (docs/security.md — idempotent, replay-resistant webhook handling).
            return Outcome.DUPLICATE;
        }

        WebhookEvent event = new WebhookEvent(UUID.randomUUID(), WebhookEvent.Source.OPEN_BANKING, PROVIDER,
                eventId, result.providerReference(), result.signatureValid(), storedPayload);

        if (!result.signatureValid()) {
            event.markProcessed(WebhookEvent.ProcessingResult.REJECTED_INVALID_SIGNATURE);
            webhookEventRepository.save(event);
            auditService.record(null, AuditEvent.ActorType.SYSTEM, PROVIDER, "WEBHOOK_REJECTED_INVALID_SIGNATURE",
                    "WebhookEvent", event.getId().toString(), null, null);
            log.warn("Rejected open-banking webhook with invalid signature or stale timestamp [eventId={}]", eventId);
            return Outcome.REJECTED_INVALID_SIGNATURE;
        }

        var applicationResult = paymentService.applyWebhookStatus(result.providerReference(), result.status());
        event.markProcessed(switch (applicationResult) {
            case APPLIED -> WebhookEvent.ProcessingResult.PROCESSED;
            case ALREADY_TERMINAL -> WebhookEvent.ProcessingResult.DUPLICATE;
            case NO_MATCHING_PAYMENT -> WebhookEvent.ProcessingResult.NO_MATCHING_PAYMENT;
        });
        webhookEventRepository.save(event);

        return applicationResult == PaymentService.WebhookApplicationResult.NO_MATCHING_PAYMENT
                ? Outcome.NO_MATCHING_PAYMENT : Outcome.PROCESSED;
    }

    private String wrapUnparseablePayload(String rawBody) {
        try {
            return objectMapper.writeValueAsString(Map.of("raw", rawBody));
        } catch (Exception e) {
            return "{\"raw\":\"<unparseable>\"}";
        }
    }
}
