package com.scansettle.api.merchant;

import com.scansettle.api.security.CurrentPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final AuthService authService;
    private final CurrentPrincipal currentPrincipal;

    public AuthController(AuthService authService, CurrentPrincipal currentPrincipal) {
        this.authService = authService;
        this.currentPrincipal = currentPrincipal;
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    /** Discriminated by which field is non-null: exactly one of the two is set. */
    public record LoginResponse(String accessToken, String mfaChallengeToken) {
    }

    @PostMapping("/api/v1/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return switch (authService.login(request.email(), request.password())) {
            case AuthService.AccessGranted granted -> new LoginResponse(granted.accessToken(), null);
            case AuthService.MfaRequired mfaRequired -> new LoginResponse(null, mfaRequired.mfaChallengeToken());
        };
    }

    public record MfaVerifyLoginRequest(@NotBlank String mfaChallengeToken, @NotBlank String code) {
    }

    public record AccessTokenResponse(String accessToken) {
    }

    @PostMapping("/api/v1/auth/mfa/verify-login")
    public AccessTokenResponse verifyLogin(@Valid @RequestBody MfaVerifyLoginRequest request) {
        return new AccessTokenResponse(authService.completeMfaLogin(request.mfaChallengeToken(), request.code()));
    }

    public record MfaEnrollResponse(String secret, String otpAuthUri) {
    }

    @PostMapping("/api/v1/auth/mfa/enroll")
    public MfaEnrollResponse enrollMfa() {
        var enrollment = authService.beginMfaEnrollment(currentPrincipal.userId());
        return new MfaEnrollResponse(enrollment.secret(), enrollment.otpAuthUri());
    }

    public record MfaVerifyRequest(@NotBlank String code) {
    }

    @PostMapping("/api/v1/auth/mfa/verify")
    public void verifyMfaEnrollment(@Valid @RequestBody MfaVerifyRequest request) {
        authService.confirmMfaEnrollment(currentPrincipal.userId(), request.code());
    }
}
