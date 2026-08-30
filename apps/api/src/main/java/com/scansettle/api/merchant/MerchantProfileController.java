package com.scansettle.api.merchant;

import com.scansettle.api.audit.AuditEvent;
import com.scansettle.api.audit.AuditService;
import com.scansettle.api.common.error.NotFoundException;
import com.scansettle.api.security.CurrentPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MerchantProfileController {

    private final MerchantRepository merchantRepository;
    private final CurrentPrincipal currentPrincipal;
    private final AuditService auditService;

    public MerchantProfileController(MerchantRepository merchantRepository, CurrentPrincipal currentPrincipal,
                                      AuditService auditService) {
        this.merchantRepository = merchantRepository;
        this.currentPrincipal = currentPrincipal;
        this.auditService = auditService;
    }

    public record ProfileResponse(String merchantId, String legalName, String tradingName, String businessType,
                                   String verificationStatus, String status) {
    }

    @GetMapping("/api/v1/merchant/profile")
    @PreAuthorize("hasRole('READ_ONLY')")
    public ProfileResponse getProfile() {
        return toResponse(currentMerchant());
    }

    public record UpdateProfileRequest(@NotBlank String tradingName, @NotBlank String businessType) {
    }

    @PatchMapping("/api/v1/merchant/profile")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ProfileResponse updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        Merchant merchant = currentMerchant();
        var before = toResponse(merchant);
        merchant.updateProfile(request.tradingName(), request.businessType());
        merchantRepository.save(merchant);

        auditService.record(merchant.getId(), AuditEvent.ActorType.MERCHANT_USER, currentPrincipal.subject(),
                "MERCHANT_PROFILE_UPDATED", "Merchant", merchant.getId().toString(), before, toResponse(merchant));
        return toResponse(merchant);
    }

    private Merchant currentMerchant() {
        return merchantRepository.findById(currentPrincipal.merchantId())
                .orElseThrow(() -> new NotFoundException("Merchant not found"));
    }

    private ProfileResponse toResponse(Merchant merchant) {
        return new ProfileResponse(merchant.getId().toString(), merchant.getLegalName(), merchant.getTradingName(),
                merchant.getBusinessType(), merchant.getVerificationStatus().name(), merchant.getStatus().name());
    }
}
