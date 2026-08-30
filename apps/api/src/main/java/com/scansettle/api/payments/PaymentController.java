package com.scansettle.api.payments;

import com.scansettle.api.common.error.NotFoundException;
import com.scansettle.api.fraud.PaymentVelocityGuard;
import com.scansettle.api.security.CurrentPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final PaymentVelocityGuard paymentVelocityGuard;
    private final CurrentPrincipal currentPrincipal;

    public PaymentController(PaymentService paymentService, PaymentRepository paymentRepository,
                              PaymentVelocityGuard paymentVelocityGuard, CurrentPrincipal currentPrincipal) {
        this.paymentService = paymentService;
        this.paymentRepository = paymentRepository;
        this.paymentVelocityGuard = paymentVelocityGuard;
        this.currentPrincipal = currentPrincipal;
    }

    public record StartPaymentResponse(String paymentId, String state, String redirectUrl) {
    }

    /** Public — a customer with no ScanSettle account starts paying a link. */
    @PostMapping("/api/v1/payment-links/{linkId}/payments")
    public ResponseEntity<StartPaymentResponse> startPayment(
            @PathVariable UUID linkId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestParam(required = false) String payerContact,
            @RequestParam(required = false) String bankId,
            HttpServletRequest request) {
        var result = paymentService.startPayment(linkId, idempotencyKey, payerContact, bankId);
        paymentVelocityGuard.checkAndFlag(result.payment().getMerchantId(), request.getRemoteAddr());
        var body = new StartPaymentResponse(
                result.payment().getId().toString(), result.payment().getState().name(), result.redirectUrl());
        return ResponseEntity.ok(body);
    }

    public record PaymentStatusResponse(String paymentId, String state, boolean terminal, long amountMinorUnits,
                                         String currencyCode, Instant updatedAt) {
    }

    /** Public — the customer's browser polls this; never trusts its own redirect outcome (docs/api.md). */
    @GetMapping("/api/v1/payments/{paymentId}/status")
    public PaymentStatusResponse status(@PathVariable UUID paymentId) {
        Payment payment = paymentService.syncAndGetStatus(paymentId);
        return toStatusResponse(payment);
    }

    public record PaymentResponse(String id, String merchantId, String paymentLinkId, long amountMinorUnits,
                                   String currencyCode, String state, String payerContact, Instant createdAt,
                                   Instant updatedAt) {
    }

    @GetMapping("/api/v1/payments")
    @PreAuthorize("hasRole('READ_ONLY')")
    public List<PaymentResponse> list(
            @RequestParam(required = false) PaymentState state,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var spec = PaymentSpecifications.search(currentPrincipal.merchantId(), state, from, to);
        Page<Payment> results = paymentRepository.findAll(spec, PageRequest.of(page, size));
        return results.map(this::toResponse).toList();
    }

    @GetMapping("/api/v1/payments/{paymentId}")
    @PreAuthorize("hasRole('READ_ONLY')")
    public PaymentResponse get(@PathVariable UUID paymentId) {
        Payment payment = paymentRepository.findByIdAndMerchantId(paymentId, currentPrincipal.merchantId())
                .orElseThrow(() -> new NotFoundException("Payment not found"));
        return toResponse(payment);
    }

    private PaymentStatusResponse toStatusResponse(Payment payment) {
        return new PaymentStatusResponse(payment.getId().toString(), payment.getState().name(),
                payment.getState().isTerminal(), payment.getAmountMinorUnits(), payment.getCurrencyCode(),
                payment.getUpdatedAt());
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(payment.getId().toString(), payment.getMerchantId().toString(),
                payment.getPaymentLinkId() != null ? payment.getPaymentLinkId().toString() : null,
                payment.getAmountMinorUnits(), payment.getCurrencyCode(), payment.getState().name(),
                payment.getPayerContact(), payment.getCreatedAt(), payment.getUpdatedAt());
    }
}
