package com.scansettle.api.merchant;

import com.scansettle.api.audit.AuditEvent;
import com.scansettle.api.audit.AuditService;
import com.scansettle.api.common.error.ApplicationException;
import com.scansettle.api.security.AuthenticatedPrincipal;
import com.scansettle.api.security.JwtService;
import com.scansettle.api.security.TotpService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthService {

    private final MerchantUserRepository merchantUserRepository;
    private final MerchantRepository merchantRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TotpService totpService;
    private final AuditService auditService;

    public AuthService(MerchantUserRepository merchantUserRepository, MerchantRepository merchantRepository,
                        PasswordEncoder passwordEncoder, JwtService jwtService, TotpService totpService,
                        AuditService auditService) {
        this.merchantUserRepository = merchantUserRepository;
        this.merchantRepository = merchantRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.totpService = totpService;
        this.auditService = auditService;
    }

    public sealed interface LoginResult permits AccessGranted, MfaRequired {
    }

    public record AccessGranted(String accessToken) implements LoginResult {
    }

    public record MfaRequired(String mfaChallengeToken) implements LoginResult {
    }

    public LoginResult login(String email, String password) {
        MerchantUser user = merchantUserRepository.findByEmail(email.toLowerCase())
                .filter(u -> u.getStatus() == MerchantUser.Status.ACTIVE)
                .orElseThrow(this::invalidCredentials);

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw invalidCredentials();
        }

        Merchant merchant = merchantRepository.findById(user.getMerchantId())
                .orElseThrow(() -> new IllegalStateException("MerchantUser references a missing Merchant"));
        if (merchant.getStatus() == Merchant.MerchantStatus.SUSPENDED) {
            throw new ApplicationException(HttpStatus.FORBIDDEN, "merchant-suspended",
                    "This account has been suspended. Contact ScanSettle support for details.");
        }

        if (user.isMfaEnabled()) {
            return new MfaRequired(jwtService.issueMfaChallenge(user.getId().toString()));
        }

        auditService.record(user.getMerchantId(), AuditEvent.ActorType.MERCHANT_USER, user.getId().toString(),
                "LOGIN_SUCCEEDED", "MerchantUser", user.getId().toString(), null, null);
        return new AccessGranted(jwtService.issue(toPrincipal(user)));
    }

    public String completeMfaLogin(String mfaChallengeToken, String code) {
        String merchantUserId = jwtService.validateMfaChallenge(mfaChallengeToken)
                .orElseThrow(() -> new ApplicationException(HttpStatus.UNAUTHORIZED, "invalid-mfa-challenge",
                        "This MFA challenge is invalid or has expired — please log in again."));

        MerchantUser user = merchantUserRepository.findById(UUID.fromString(merchantUserId))
                .orElseThrow(() -> new ApplicationException(HttpStatus.UNAUTHORIZED, "invalid-mfa-challenge",
                        "This MFA challenge is invalid or has expired — please log in again."));

        if (!totpService.verify(user.getMfaSecret(), code)) {
            throw new ApplicationException(HttpStatus.UNAUTHORIZED, "invalid-mfa-code", "Incorrect verification code.");
        }

        auditService.record(user.getMerchantId(), AuditEvent.ActorType.MERCHANT_USER, user.getId().toString(),
                "LOGIN_SUCCEEDED_MFA", "MerchantUser", user.getId().toString(), null, null);
        return jwtService.issue(toPrincipal(user));
    }

    public record MfaEnrollment(String secret, String otpAuthUri) {
    }

    @Transactional
    public MfaEnrollment beginMfaEnrollment(UUID merchantUserId) {
        MerchantUser user = merchantUserRepository.findById(merchantUserId).orElseThrow();
        String secret = totpService.generateSecret();
        user.beginMfaEnrollment(secret);
        merchantUserRepository.save(user);
        return new MfaEnrollment(secret, totpService.buildOtpAuthUri(secret, user.getEmail()));
    }

    @Transactional
    public void confirmMfaEnrollment(UUID merchantUserId, String code) {
        MerchantUser user = merchantUserRepository.findById(merchantUserId).orElseThrow();
        if (user.getMfaSecret() == null || !totpService.verify(user.getMfaSecret(), code)) {
            throw new ApplicationException(HttpStatus.BAD_REQUEST, "invalid-mfa-code",
                    "Incorrect verification code — scan the QR code again and try the current code.");
        }
        user.confirmMfaEnrollment();
        merchantUserRepository.save(user);
        auditService.record(user.getMerchantId(), AuditEvent.ActorType.MERCHANT_USER, user.getId().toString(),
                "MFA_ENABLED", "MerchantUser", user.getId().toString(), null, null);
    }

    private AuthenticatedPrincipal toPrincipal(MerchantUser user) {
        return new AuthenticatedPrincipal(
                user.getEmail(), user.getRole(), user.getMerchantId().toString(), user.getId().toString());
    }

    private ApplicationException invalidCredentials() {
        return new ApplicationException(HttpStatus.UNAUTHORIZED, "invalid-credentials", "Incorrect email or password.");
    }
}
