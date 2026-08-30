package com.scansettle.api.merchant;

import com.scansettle.api.audit.AuditEvent;
import com.scansettle.api.audit.AuditService;
import com.scansettle.api.common.error.ApplicationException;
import com.scansettle.api.common.error.NotFoundException;
import com.scansettle.api.security.CurrentPrincipal;
import com.scansettle.api.security.TotpService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Bank account changes are the single most fraud-sensitive action a merchant user
 * can take (docs/security.md) — ADMIN+ only, every change requires re-authentication
 * (current password, plus a fresh TOTP code if MFA is enabled — Phase 9 hardening),
 * and every change (successful or a failed step-up attempt) is audited with a
 * dedicated high-visibility action name.
 */
@RestController
public class MerchantBankAccountController {

    private final MerchantBankAccountRepository bankAccountRepository;
    private final MerchantUserRepository merchantUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final TotpService totpService;
    private final CurrentPrincipal currentPrincipal;
    private final AuditService auditService;

    public MerchantBankAccountController(MerchantBankAccountRepository bankAccountRepository,
                                          MerchantUserRepository merchantUserRepository,
                                          PasswordEncoder passwordEncoder, TotpService totpService,
                                          CurrentPrincipal currentPrincipal, AuditService auditService) {
        this.bankAccountRepository = bankAccountRepository;
        this.merchantUserRepository = merchantUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.totpService = totpService;
        this.currentPrincipal = currentPrincipal;
        this.auditService = auditService;
    }

    public record BankAccountResponse(String id, String maskedAccountNumber, String accountName, boolean verified) {
    }

    @GetMapping("/api/v1/merchant/bank-account")
    @PreAuthorize("hasRole('ADMIN')")
    public BankAccountResponse getBankAccount() {
        MerchantBankAccount account = bankAccountRepository
                .findFirstByMerchantIdAndStatusOrderByCreatedAtDesc(
                        currentPrincipal.merchantId(), MerchantBankAccount.Status.ACTIVE)
                .orElseThrow(() -> new NotFoundException("No bank account on file"));
        return toResponse(account);
    }

    public record SetBankAccountRequest(
            @NotBlank @Pattern(regexp = "\\d{6}", message = "Sort code must be 6 digits") String sortCode,
            @NotBlank @Pattern(regexp = "\\d{8}", message = "Account number must be 8 digits") String accountNumber,
            @NotBlank String accountName,
            @NotBlank String currentPassword,
            String mfaCode) {
    }

    @PutMapping("/api/v1/merchant/bank-account")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public BankAccountResponse setBankAccount(@Valid @RequestBody SetBankAccountRequest request) {
        UUID merchantId = currentPrincipal.merchantId();
        stepUpOrThrow(merchantId, request);

        // Replace, don't overwrite in place — the old (now REPLACED) row stays for audit history.
        bankAccountRepository.findFirstByMerchantIdAndStatusOrderByCreatedAtDesc(
                merchantId, MerchantBankAccount.Status.ACTIVE
        ).ifPresent(existing -> {
            existing.markReplaced();
            bankAccountRepository.save(existing);
        });

        MerchantBankAccount account = new MerchantBankAccount(
                UUID.randomUUID(), merchantId, request.sortCode(), request.accountNumber(), request.accountName());
        bankAccountRepository.save(account);

        auditService.record(merchantId, AuditEvent.ActorType.MERCHANT_USER, currentPrincipal.subject(),
                "BANK_ACCOUNT_CHANGED", "MerchantBankAccount", account.getId().toString(), null,
                new BankAccountAuditSnapshot(account.getAccountName(), account.getMaskedAccountNumber()));

        return toResponse(account);
    }

    /** Re-verifies the caller's own credentials before a bank-account change proceeds
     *  — a valid session token alone isn't enough for the single most fraud-sensitive
     *  action a merchant user can take (docs/security.md). Failed attempts are audited
     *  too, since a wrong password on this specific endpoint is itself high-signal. */
    private void stepUpOrThrow(UUID merchantId, SetBankAccountRequest request) {
        MerchantUser user = merchantUserRepository.findById(currentPrincipal.userId())
                .orElseThrow(() -> new IllegalStateException("Authenticated principal has no MerchantUser row"));

        boolean passwordOk = passwordEncoder.matches(request.currentPassword(), user.getPasswordHash());
        boolean mfaOk = !user.isMfaEnabled()
                || (request.mfaCode() != null && totpService.verify(user.getMfaSecret(), request.mfaCode()));

        if (!passwordOk || !mfaOk) {
            auditService.record(merchantId, AuditEvent.ActorType.MERCHANT_USER, currentPrincipal.subject(),
                    "BANK_ACCOUNT_CHANGE_STEP_UP_FAILED", "MerchantUser", user.getId().toString(), null, null);
            throw new ApplicationException(HttpStatus.UNAUTHORIZED, "step-up-failed",
                    !passwordOk ? "Incorrect password." : "Incorrect or missing verification code.");
        }
    }

    private BankAccountResponse toResponse(MerchantBankAccount account) {
        return new BankAccountResponse(
                account.getId().toString(), account.getMaskedAccountNumber(), account.getAccountName(), account.isVerified());
    }

    private record BankAccountAuditSnapshot(String accountName, String maskedAccountNumber) {
    }
}
