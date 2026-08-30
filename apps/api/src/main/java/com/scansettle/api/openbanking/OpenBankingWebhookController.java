package com.scansettle.api.openbanking;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public — a provider authenticates a webhook by signature, never a merchant JWT
 * (docs/security.md). Thin HTTP adapter; all real logic — signature verification,
 * idempotent deduplication, applying the resulting state to the Payment — lives in
 * {@link WebhookIngestionService}, shared with the local mock-bank decision endpoint.
 */
@RestController
public class OpenBankingWebhookController {

    private final WebhookIngestionService webhookIngestionService;

    public OpenBankingWebhookController(WebhookIngestionService webhookIngestionService) {
        this.webhookIngestionService = webhookIngestionService;
    }

    @PostMapping("/api/v1/webhooks/open-banking")
    public ResponseEntity<Void> receive(@RequestHeader Map<String, String> headers, @RequestBody String rawBody) {
        var outcome = webhookIngestionService.ingestOpenBankingWebhook(headers, rawBody);
        return outcome == WebhookIngestionService.Outcome.REJECTED_INVALID_SIGNATURE
                ? ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
                : ResponseEntity.ok().build();
    }
}
