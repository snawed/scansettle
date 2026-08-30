package com.scansettle.api.admin;

import com.scansettle.api.audit.AuditEvent;
import com.scansettle.api.audit.AuditService;
import com.scansettle.api.common.error.NotFoundException;
import com.scansettle.api.merchant.Merchant;
import com.scansettle.api.merchant.MerchantRepository;
import com.scansettle.api.security.CurrentPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/** ScanSettle ops' cross-merchant visibility — never scoped to a single merchant, unlike every other controller in this codebase. */
@RestController
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class AdminMerchantController {

    private final MerchantRepository merchantRepository;
    private final AuditService auditService;
    private final CurrentPrincipal currentPrincipal;

    public AdminMerchantController(MerchantRepository merchantRepository, AuditService auditService,
                                    CurrentPrincipal currentPrincipal) {
        this.merchantRepository = merchantRepository;
        this.auditService = auditService;
        this.currentPrincipal = currentPrincipal;
    }

    public record MerchantSummary(String id, String legalName, String tradingName, String businessType,
                                   String verificationStatus, String status, Instant createdAt) {
    }

    @GetMapping("/api/v1/admin/merchants")
    public java.util.List<MerchantSummary> list(@RequestParam(required = false) String q,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "50") int size) {
        Page<Merchant> results = (q == null || q.isBlank())
                ? merchantRepository.findAll(PageRequest.of(page, size))
                : merchantRepository.findByTradingNameContainingIgnoreCase(q, PageRequest.of(page, size));
        return results.map(this::toResponse).toList();
    }

    @PostMapping("/api/v1/admin/merchants/{id}/suspend")
    public MerchantSummary suspend(@PathVariable UUID id) {
        Merchant merchant = merchantRepository.findById(id).orElseThrow(() -> new NotFoundException("Merchant not found"));
        merchant.suspend();
        merchantRepository.save(merchant);
        auditService.record(merchant.getId(), AuditEvent.ActorType.OPS, currentPrincipal.userId().toString(),
                "MERCHANT_SUSPENDED", "Merchant", merchant.getId().toString(), null, null);
        return toResponse(merchant);
    }

    @PostMapping("/api/v1/admin/merchants/{id}/reactivate")
    public MerchantSummary reactivate(@PathVariable UUID id) {
        Merchant merchant = merchantRepository.findById(id).orElseThrow(() -> new NotFoundException("Merchant not found"));
        merchant.reactivate();
        merchantRepository.save(merchant);
        auditService.record(merchant.getId(), AuditEvent.ActorType.OPS, currentPrincipal.userId().toString(),
                "MERCHANT_REACTIVATED", "Merchant", merchant.getId().toString(), null, null);
        return toResponse(merchant);
    }

    private MerchantSummary toResponse(Merchant merchant) {
        return new MerchantSummary(merchant.getId().toString(), merchant.getLegalName(), merchant.getTradingName(),
                merchant.getBusinessType(), merchant.getVerificationStatus().name(), merchant.getStatus().name(),
                merchant.getCreatedAt());
    }
}
