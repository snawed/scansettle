package com.scansettle.api.payments;

import com.scansettle.api.merchant.Merchant;
import com.scansettle.api.merchant.MerchantRepository;
import com.scansettle.api.security.CurrentPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import java.time.Instant;
import java.util.UUID;

@RestController
public class PaymentLinkController {

    private final PaymentLinkService paymentLinkService;
    private final QrCodeService qrCodeService;
    private final MerchantRepository merchantRepository;
    private final CurrentPrincipal currentPrincipal;

    public PaymentLinkController(PaymentLinkService paymentLinkService, QrCodeService qrCodeService,
                                  MerchantRepository merchantRepository, CurrentPrincipal currentPrincipal) {
        this.paymentLinkService = paymentLinkService;
        this.qrCodeService = qrCodeService;
        this.merchantRepository = merchantRepository;
        this.currentPrincipal = currentPrincipal;
    }

    public record CreatePaymentLinkRequest(
            @Positive long amountMinorUnits,
            @NotBlank String currencyCode,
            @NotBlank String description,
            @NotBlank String reference,
            Instant expiresAt) {
    }

    public record PaymentLinkResponse(String id, long amountMinorUnits, String currencyCode, String description,
                                       String reference, String status, String url, Instant expiresAt,
                                       Instant createdAt) {
    }

    @PostMapping("/api/v1/payment-links")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<PaymentLinkResponse> create(@Valid @RequestBody CreatePaymentLinkRequest request) {
        PaymentLink link = paymentLinkService.create(
                currentPrincipal.merchantId(), currentPrincipal.userId(), request.amountMinorUnits(),
                request.currencyCode(), request.description(), request.reference(), request.expiresAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(link));
    }

    @GetMapping("/api/v1/payment-links")
    @PreAuthorize("hasRole('READ_ONLY')")
    public List<PaymentLinkResponse> list(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        Page<PaymentLink> results = paymentLinkService.list(currentPrincipal.merchantId(), PageRequest.of(page, size));
        return results.map(this::toResponse).toList();
    }

    @GetMapping("/api/v1/payment-links/{linkId}")
    @PreAuthorize("hasRole('READ_ONLY')")
    public PaymentLinkResponse get(@PathVariable UUID linkId) {
        return toResponse(paymentLinkService.getForMerchant(linkId, currentPrincipal.merchantId()));
    }

    @GetMapping("/api/v1/payment-links/{linkId}/qr")
    @PreAuthorize("hasRole('READ_ONLY')")
    public ResponseEntity<byte[]> qr(@PathVariable UUID linkId) {
        PaymentLink link = paymentLinkService.getForMerchant(linkId, currentPrincipal.merchantId());
        byte[] png = qrCodeService.generatePng(paymentLinkService.publicUrl(link));
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(png);
    }

    /** Public — the pay page needs this before the customer has any relationship with ScanSettle. */
    @GetMapping("/api/v1/payment-links/{linkId}/public")
    public PublicPaymentLinkResponse getPublic(@PathVariable UUID linkId) {
        PaymentLink link = paymentLinkService.getPublic(linkId);
        String merchantTradingName = merchantRepository.findById(link.getMerchantId())
                .map(Merchant::getTradingName)
                .orElse("Unknown merchant");
        return new PublicPaymentLinkResponse(link.getId().toString(), link.getAmountMinorUnits(),
                link.getCurrencyCode(), link.getDescription(), link.getReference(), link.isPayable(),
                merchantTradingName);
    }

    public record PublicPaymentLinkResponse(String id, long amountMinorUnits, String currencyCode,
                                             String description, String reference, boolean payable,
                                             String merchantTradingName) {
    }

    private PaymentLinkResponse toResponse(PaymentLink link) {
        return new PaymentLinkResponse(link.getId().toString(), link.getAmountMinorUnits(), link.getCurrencyCode(),
                link.getDescription(), link.getReference(), link.getStatus().name(), paymentLinkService.publicUrl(link),
                link.getExpiresAt(), link.getCreatedAt());
    }
}
