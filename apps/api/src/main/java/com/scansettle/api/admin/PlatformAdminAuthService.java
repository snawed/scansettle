package com.scansettle.api.admin;

import com.scansettle.api.audit.AuditEvent;
import com.scansettle.api.audit.AuditService;
import com.scansettle.api.common.error.ApplicationException;
import com.scansettle.api.security.AuthenticatedPrincipal;
import com.scansettle.api.security.JwtService;
import com.scansettle.api.security.Role;
import com.scansettle.api.security.TotpService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PlatformAdminAuthService {

    private final PlatformAdminUserRepository platformAdminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TotpService totpService;
    private final AuditService auditService;

    public PlatformAdminAuthService(PlatformAdminUserRepository platformAdminUserRepository,
                                     PasswordEncoder passwordEncoder, JwtService jwtService,
                                     TotpService totpService, AuditService auditService) {
        this.platformAdminUserRepository = platformAdminUserRepository;
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
        PlatformAdminUser user = platformAdminUserRepository.findByEmail(email.toLowerCase())
                .orElseThrow(this::invalidCredentials);

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw invalidCredentials();
        }

        if (user.isMfaEnabled()) {
            return new MfaRequired(jwtService.issueMfaChallenge(user.getId().toString()));
        }

        auditService.record(null, AuditEvent.ActorType.OPS, user.getId().toString(),
                "LOGIN_SUCCEEDED", "PlatformAdminUser", user.getId().toString(), null, null);
        return new AccessGranted(jwtService.issue(toPrincipal(user)));
    }

    public String completeMfaLogin(String mfaChallengeToken, String code) {
        String platformAdminUserId = jwtService.validateMfaChallenge(mfaChallengeToken)
                .orElseThrow(() -> new ApplicationException(HttpStatus.UNAUTHORIZED, "invalid-mfa-challenge",
                        "This MFA challenge is invalid or has expired — please log in again."));

        PlatformAdminUser user = platformAdminUserRepository.findById(UUID.fromString(platformAdminUserId))
                .orElseThrow(() -> new ApplicationException(HttpStatus.UNAUTHORIZED, "invalid-mfa-challenge",
                        "This MFA challenge is invalid or has expired — please log in again."));

        if (!totpService.verify(user.getMfaSecret(), code)) {
            throw new ApplicationException(HttpStatus.UNAUTHORIZED, "invalid-mfa-code", "Incorrect verification code.");
        }

        auditService.record(null, AuditEvent.ActorType.OPS, user.getId().toString(),
                "LOGIN_SUCCEEDED_MFA", "PlatformAdminUser", user.getId().toString(), null, null);
        return jwtService.issue(toPrincipal(user));
    }

    public record MfaEnrollment(String secret, String otpAuthUri) {
    }

    @Transactional
    public MfaEnrollment beginMfaEnrollment(UUID platformAdminUserId) {
        PlatformAdminUser user = platformAdminUserRepository.findById(platformAdminUserId).orElseThrow();
        String secret = totpService.generateSecret();
        user.beginMfaEnrollment(secret);
        platformAdminUserRepository.save(user);
        return new MfaEnrollment(secret, totpService.buildOtpAuthUri(secret, user.getEmail()));
    }

    @Transactional
    public void confirmMfaEnrollment(UUID platformAdminUserId, String code) {
        PlatformAdminUser user = platformAdminUserRepository.findById(platformAdminUserId).orElseThrow();
        if (user.getMfaSecret() == null || !totpService.verify(user.getMfaSecret(), code)) {
            throw new ApplicationException(HttpStatus.BAD_REQUEST, "invalid-mfa-code",
                    "Incorrect verification code — scan the QR code again and try the current code.");
        }
        user.confirmMfaEnrollment();
        platformAdminUserRepository.save(user);
        auditService.record(null, AuditEvent.ActorType.OPS, user.getId().toString(),
                "MFA_ENABLED", "PlatformAdminUser", user.getId().toString(), null, null);
    }

    private AuthenticatedPrincipal toPrincipal(PlatformAdminUser user) {
        // merchantId is deliberately null — a platform admin is never scoped to one
        // merchant (AuthenticatedPrincipal's contract already anticipates this).
        return new AuthenticatedPrincipal(user.getEmail(), Role.PLATFORM_ADMIN, null, user.getId().toString());
    }

    private ApplicationException invalidCredentials() {
        return new ApplicationException(HttpStatus.UNAUTHORIZED, "invalid-credentials", "Incorrect email or password.");
    }
}
