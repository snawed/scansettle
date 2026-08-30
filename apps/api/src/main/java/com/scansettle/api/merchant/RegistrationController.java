package com.scansettle.api.merchant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Public — a business signs up with no prior relationship to ScanSettle. */
@RestController
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    public record RegisterRequest(
            @NotBlank String legalName,
            @NotBlank String tradingName,
            @NotBlank String businessType,
            @Email @NotBlank String email,
            @NotBlank @Size(min = 10, message = "Password must be at least 10 characters") String password) {
    }

    public record RegisterResponse(String merchantId, String tradingName, String ownerEmail) {
    }

    @PostMapping("/api/v1/merchants")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        var result = registrationService.register(
                request.legalName(), request.tradingName(), request.businessType(),
                request.email(), request.password());

        var body = new RegisterResponse(
                result.merchant().getId().toString(), result.merchant().getTradingName(), result.owner().getEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}
