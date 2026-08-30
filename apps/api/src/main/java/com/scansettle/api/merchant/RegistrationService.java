package com.scansettle.api.merchant;

import com.scansettle.api.audit.AuditEvent;
import com.scansettle.api.audit.AuditService;
import com.scansettle.api.common.error.ApplicationException;
import com.scansettle.api.pricing.PlanCode;
import com.scansettle.api.pricing.PricingPlanRepository;
import com.scansettle.api.security.Role;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RegistrationService {

    private final MerchantRepository merchantRepository;
    private final MerchantUserRepository merchantUserRepository;
    private final PricingPlanRepository pricingPlanRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public RegistrationService(MerchantRepository merchantRepository, MerchantUserRepository merchantUserRepository,
                                PricingPlanRepository pricingPlanRepository, PasswordEncoder passwordEncoder,
                                AuditService auditService) {
        this.merchantRepository = merchantRepository;
        this.merchantUserRepository = merchantUserRepository;
        this.pricingPlanRepository = pricingPlanRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    public record RegistrationResult(Merchant merchant, MerchantUser owner) {
    }

    @Transactional
    public RegistrationResult register(String legalName, String tradingName, String businessType,
                                        String email, String password) {
        String normalizedEmail = email.toLowerCase();
        if (merchantUserRepository.existsByEmail(normalizedEmail)) {
            throw new ApplicationException(HttpStatus.CONFLICT, "email-already-registered",
                    "An account with this email already exists.");
        }

        var basicPlan = pricingPlanRepository.findByCode(PlanCode.BASIC)
                .orElseThrow(() -> new IllegalStateException("BASIC pricing plan is not seeded"));

        Merchant merchant = new Merchant(UUID.randomUUID(), legalName, tradingName, businessType, basicPlan.getId());
        merchantRepository.save(merchant);

        MerchantUser owner = new MerchantUser(
                UUID.randomUUID(), merchant.getId(), normalizedEmail, passwordEncoder.encode(password), Role.OWNER);
        merchantUserRepository.save(owner);

        auditService.record(merchant.getId(), AuditEvent.ActorType.MERCHANT_USER, owner.getId().toString(),
                "MERCHANT_REGISTERED", "Merchant", merchant.getId().toString(), null,
                new MerchantSnapshot(merchant.getTradingName(), merchant.getBusinessType()));

        return new RegistrationResult(merchant, owner);
    }

    private record MerchantSnapshot(String tradingName, String businessType) {
    }
}
