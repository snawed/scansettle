package com.scansettle.api.admin;

import com.scansettle.api.audit.AuditEvent;
import com.scansettle.api.audit.AuditService;
import com.scansettle.api.common.error.ApplicationException;
import com.scansettle.api.common.error.NotFoundException;
import com.scansettle.api.security.CurrentPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Raise/clear a fraud flag against a merchant or a specific payment. */
@RestController
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class AdminFraudFlagController {

    private final FraudFlagRepository fraudFlagRepository;
    private final AuditService auditService;
    private final CurrentPrincipal currentPrincipal;

    public AdminFraudFlagController(FraudFlagRepository fraudFlagRepository, AuditService auditService,
                                     CurrentPrincipal currentPrincipal) {
        this.fraudFlagRepository = fraudFlagRepository;
        this.auditService = auditService;
        this.currentPrincipal = currentPrincipal;
    }

    public record RaiseRequest(UUID merchantId, UUID paymentId, @NotBlank String reason) {
    }

    public record FraudFlagResponse(String id, String merchantId, String paymentId, String reason, String status,
                                     String raisedBy, Instant raisedAt, String clearedBy, Instant clearedAt) {
    }

    @GetMapping("/api/v1/admin/fraud-flags")
    public List<FraudFlagResponse> list(@RequestParam(required = false) UUID merchantId) {
        List<FraudFlag> flags = merchantId == null
                ? fraudFlagRepository.findAllByOrderByRaisedAtDesc()
                : fraudFlagRepository.findByMerchantIdOrderByRaisedAtDesc(merchantId);
        return flags.stream().map(this::toResponse).toList();
    }

    @PostMapping("/api/v1/admin/fraud-flags")
    public ResponseEntity<FraudFlagResponse> raise(@Valid @RequestBody RaiseRequest request) {
        boolean exactlyOneTarget = (request.merchantId() != null) ^ (request.paymentId() != null);
        if (!exactlyOneTarget) {
            throw new ApplicationException(HttpStatus.BAD_REQUEST, "exactly-one-target",
                    "Provide exactly one of merchantId or paymentId.");
        }

        UUID raisedBy = currentPrincipal.userId();
        FraudFlag flag = new FraudFlag(UUID.randomUUID(), request.merchantId(), request.paymentId(),
                request.reason(), raisedBy);
        fraudFlagRepository.save(flag);

        auditService.record(request.merchantId(), AuditEvent.ActorType.OPS, raisedBy.toString(), "FRAUD_FLAG_RAISED",
                "FraudFlag", flag.getId().toString(), null, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(flag));
    }

    @PostMapping("/api/v1/admin/fraud-flags/{id}/clear")
    public FraudFlagResponse clear(@PathVariable UUID id) {
        FraudFlag flag = fraudFlagRepository.findById(id).orElseThrow(() -> new NotFoundException("Fraud flag not found"));
        UUID clearedBy = currentPrincipal.userId();
        flag.clear(clearedBy);
        fraudFlagRepository.save(flag);

        auditService.record(flag.getMerchantId(), AuditEvent.ActorType.OPS, clearedBy.toString(), "FRAUD_FLAG_CLEARED",
                "FraudFlag", flag.getId().toString(), null, null);
        return toResponse(flag);
    }

    private FraudFlagResponse toResponse(FraudFlag flag) {
        return new FraudFlagResponse(flag.getId().toString(),
                flag.getMerchantId() != null ? flag.getMerchantId().toString() : null,
                flag.getPaymentId() != null ? flag.getPaymentId().toString() : null,
                flag.getReason(), flag.getStatus().name(),
                flag.getRaisedBy() != null ? flag.getRaisedBy().toString() : "SYSTEM", flag.getRaisedAt(),
                flag.getClearedBy() != null ? flag.getClearedBy().toString() : null, flag.getClearedAt());
    }
}
