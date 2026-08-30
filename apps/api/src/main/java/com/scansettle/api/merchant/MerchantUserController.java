package com.scansettle.api.merchant;

import com.scansettle.api.audit.AuditEvent;
import com.scansettle.api.audit.AuditService;
import com.scansettle.api.common.error.ApplicationException;
import com.scansettle.api.common.error.NotFoundException;
import com.scansettle.api.security.CurrentPrincipal;
import com.scansettle.api.security.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Staff/RBAC management — ADMIN+ only for writes (docs/api.md, docs/security.md). */
@RestController
public class MerchantUserController {

    private final MerchantUserRepository merchantUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentPrincipal currentPrincipal;
    private final AuditService auditService;

    public MerchantUserController(MerchantUserRepository merchantUserRepository, PasswordEncoder passwordEncoder,
                                   CurrentPrincipal currentPrincipal, AuditService auditService) {
        this.merchantUserRepository = merchantUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentPrincipal = currentPrincipal;
        this.auditService = auditService;
    }

    public record MerchantUserResponse(String id, String email, String role, boolean mfaEnabled, String status) {
    }

    @GetMapping("/api/v1/merchant-users")
    @PreAuthorize("hasRole('READ_ONLY')")
    public List<MerchantUserResponse> list() {
        return merchantUserRepository.findByMerchantId(currentPrincipal.merchantId()).stream()
                .map(this::toResponse)
                .toList();
    }

    public record CreateMerchantUserRequest(
            @Email @NotBlank String email,
            @NotNull Role role,
            @NotBlank @Size(min = 10, message = "Temporary password must be at least 10 characters")
            String temporaryPassword) {
    }

    @PostMapping("/api/v1/merchant-users")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<MerchantUserResponse> create(@Valid @RequestBody CreateMerchantUserRequest request) {
        // No email/invite delivery yet (notification module is a later phase) — the
        // ADMIN/OWNER creating the account communicates the temporary password
        // out-of-band. Flagged as a known simplification, not silently skipped.
        String normalizedEmail = request.email().toLowerCase();
        if (merchantUserRepository.existsByEmail(normalizedEmail)) {
            throw new ApplicationException(HttpStatus.CONFLICT, "email-already-registered",
                    "A user with this email already exists.");
        }

        MerchantUser user = new MerchantUser(UUID.randomUUID(), currentPrincipal.merchantId(), normalizedEmail,
                passwordEncoder.encode(request.temporaryPassword()), request.role());
        merchantUserRepository.save(user);

        auditService.record(currentPrincipal.merchantId(), AuditEvent.ActorType.MERCHANT_USER,
                currentPrincipal.subject(), "MERCHANT_USER_CREATED", "MerchantUser", user.getId().toString(),
                null, new RoleSnapshot(user.getEmail(), user.getRole().name()));

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(user));
    }

    public record ChangeRoleRequest(@NotNull Role role) {
    }

    @PatchMapping("/api/v1/merchant-users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public MerchantUserResponse changeRole(@PathVariable UUID id, @Valid @RequestBody ChangeRoleRequest request) {
        MerchantUser user = merchantUserRepository.findByIdAndMerchantId(id, currentPrincipal.merchantId())
                .orElseThrow(() -> new NotFoundException("Merchant user not found"));

        var before = new RoleSnapshot(user.getEmail(), user.getRole().name());
        user.changeRole(request.role());
        merchantUserRepository.save(user);

        auditService.record(currentPrincipal.merchantId(), AuditEvent.ActorType.MERCHANT_USER,
                currentPrincipal.subject(), "MERCHANT_USER_ROLE_CHANGED", "MerchantUser", user.getId().toString(),
                before, new RoleSnapshot(user.getEmail(), user.getRole().name()));

        return toResponse(user);
    }

    private MerchantUserResponse toResponse(MerchantUser user) {
        return new MerchantUserResponse(
                user.getId().toString(), user.getEmail(), user.getRole().name(), user.isMfaEnabled(),
                user.getStatus().name());
    }

    private record RoleSnapshot(String email, String role) {
    }
}
