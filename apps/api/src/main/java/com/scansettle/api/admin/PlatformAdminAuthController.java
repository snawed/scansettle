package com.scansettle.api.admin;

import com.scansettle.api.security.CurrentPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Public login (+ MFA) for ScanSettle's own ops/support staff — see {@link PlatformAdminUser}. */
@RestController
public class PlatformAdminAuthController {

    private final PlatformAdminAuthService platformAdminAuthService;
    private final CurrentPrincipal currentPrincipal;

    public PlatformAdminAuthController(PlatformAdminAuthService platformAdminAuthService,
                                        CurrentPrincipal currentPrincipal) {
        this.platformAdminAuthService = platformAdminAuthService;
        this.currentPrincipal = currentPrincipal;
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    /** Discriminated by which field is non-null: exactly one of the two is set. */
    public record LoginResponse(String accessToken, String mfaChallengeToken) {
    }

    @PostMapping("/api/v1/admin/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return switch (platformAdminAuthService.login(request.email(), request.password())) {
            case PlatformAdminAuthService.AccessGranted granted -> new LoginResponse(granted.accessToken(), null);
            case PlatformAdminAuthService.MfaRequired mfaRequired -> new LoginResponse(null, mfaRequired.mfaChallengeToken());
        };
    }

    public record MfaVerifyLoginRequest(@NotBlank String mfaChallengeToken, @NotBlank String code) {
    }

    public record AccessTokenResponse(String accessToken) {
    }

    @PostMapping("/api/v1/admin/auth/mfa/verify-login")
    public AccessTokenResponse verifyLogin(@Valid @RequestBody MfaVerifyLoginRequest request) {
        return new AccessTokenResponse(platformAdminAuthService.completeMfaLogin(request.mfaChallengeToken(), request.code()));
    }

    public record MfaEnrollResponse(String secret, String otpAuthUri) {
    }

    @PostMapping("/api/v1/admin/auth/mfa/enroll")
    public MfaEnrollResponse enrollMfa() {
        var enrollment = platformAdminAuthService.beginMfaEnrollment(currentPrincipal.userId());
        return new MfaEnrollResponse(enrollment.secret(), enrollment.otpAuthUri());
    }

    public record MfaVerifyRequest(@NotBlank String code) {
    }

    @PostMapping("/api/v1/admin/auth/mfa/verify")
    public void verifyMfaEnrollment(@Valid @RequestBody MfaVerifyRequest request) {
        platformAdminAuthService.confirmMfaEnrollment(currentPrincipal.userId(), request.code());
    }
}
