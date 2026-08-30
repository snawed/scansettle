package com.scansettle.api.tables;

import com.scansettle.api.fraud.PaymentVelocityGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class BillPaymentController {

    private final BillPaymentService billPaymentService;
    private final PaymentVelocityGuard paymentVelocityGuard;

    public BillPaymentController(BillPaymentService billPaymentService, PaymentVelocityGuard paymentVelocityGuard) {
        this.billPaymentService = billPaymentService;
        this.paymentVelocityGuard = paymentVelocityGuard;
    }

    public record StartBillPaymentRequest(long contributionAmountMinorUnits,
                                           @PositiveOrZero long tipAmountMinorUnits,
                                           BillPayment.TipMethod tipMethod, String payerContact) {
    }

    public record StartBillPaymentResponse(String paymentId, String state, String redirectUrl) {
    }

    /** Public — a customer with no ScanSettle account contributes to a shared bill. */
    @PostMapping("/api/v1/bills/{billId}/payments")
    public ResponseEntity<StartBillPaymentResponse> startPayment(
            @PathVariable UUID billId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestParam(required = false) String bankId,
            @RequestBody StartBillPaymentRequest request,
            HttpServletRequest httpRequest) {
        var result = billPaymentService.startBillPayment(billId, request.contributionAmountMinorUnits(),
                request.tipAmountMinorUnits(), request.tipMethod() == null ? BillPayment.TipMethod.NONE : request.tipMethod(),
                request.payerContact(), idempotencyKey, bankId);
        paymentVelocityGuard.checkAndFlag(result.payment().getMerchantId(), httpRequest.getRemoteAddr());

        var body = new StartBillPaymentResponse(
                result.payment().getId().toString(), result.payment().getState().name(), result.redirectUrl());
        return ResponseEntity.ok(body);
    }
}
