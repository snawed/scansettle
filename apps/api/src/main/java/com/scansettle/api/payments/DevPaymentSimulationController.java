package com.scansettle.api.payments;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Stands in for the real webhook (Phase 4 — signature validation, idempotent
 * ingestion) so the payment lifecycle is demonstrable end-to-end before that
 * infrastructure exists. dev/test profiles only.
 */
@RestController
@Profile({"dev", "test"})
public class DevPaymentSimulationController {

    private final PaymentService paymentService;

    public DevPaymentSimulationController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    public record SimulationResponse(String paymentId, String state) {
    }

    @PostMapping("/api/v1/dev/payments/{paymentId}/simulate-provider-status")
    public SimulationResponse simulate(@PathVariable UUID paymentId, @RequestParam boolean confirm) {
        Payment payment = paymentService.simulateProviderConfirmation(paymentId, confirm);
        return new SimulationResponse(payment.getId().toString(), payment.getState().name());
    }
}
