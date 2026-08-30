package com.scansettle.api.payments;

import com.scansettle.api.audit.AuditEvent;
import com.scansettle.api.audit.AuditService;
import com.scansettle.api.common.error.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class PaymentLinkService {

    private final PaymentLinkRepository paymentLinkRepository;
    private final AuditService auditService;
    private final String linksBaseUrl;

    public PaymentLinkService(PaymentLinkRepository paymentLinkRepository, AuditService auditService,
                               @Value("${app.links.base-url}") String linksBaseUrl) {
        this.paymentLinkRepository = paymentLinkRepository;
        this.auditService = auditService;
        this.linksBaseUrl = linksBaseUrl;
    }

    @Transactional
    public PaymentLink create(UUID merchantId, UUID createdBy, long amountMinorUnits, String currencyCode,
                               String description, String reference, Instant expiresAt) {
        PaymentLink link = new PaymentLink(
                UUID.randomUUID(), merchantId, amountMinorUnits, currencyCode, description, reference,
                expiresAt, createdBy);
        paymentLinkRepository.save(link);

        auditService.record(merchantId, AuditEvent.ActorType.MERCHANT_USER, createdBy.toString(),
                "PAYMENT_LINK_CREATED", "PaymentLink", link.getId().toString(), null,
                new LinkSnapshot(amountMinorUnits, currencyCode, description, reference));

        return link;
    }

    public Page<PaymentLink> list(UUID merchantId, Pageable pageable) {
        return paymentLinkRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId, pageable);
    }

    public PaymentLink getForMerchant(UUID linkId, UUID merchantId) {
        return paymentLinkRepository.findByIdAndMerchantId(linkId, merchantId)
                .orElseThrow(() -> new NotFoundException("Payment link not found"));
    }

    /** No merchant scoping — this backs the public customer pay page. */
    public PaymentLink getPublic(UUID linkId) {
        return paymentLinkRepository.findById(linkId)
                .orElseThrow(() -> new NotFoundException("Payment link not found"));
    }

    public String publicUrl(PaymentLink link) {
        return linksBaseUrl + "/" + link.getId();
    }

    private record LinkSnapshot(long amountMinorUnits, String currencyCode, String description, String reference) {
    }
}
