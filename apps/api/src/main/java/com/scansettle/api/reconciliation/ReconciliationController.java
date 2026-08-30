package com.scansettle.api.reconciliation;

import com.scansettle.api.security.CurrentPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
public class ReconciliationController {

    private final ReconciliationRecordRepository reconciliationRecordRepository;
    private final CurrentPrincipal currentPrincipal;

    public ReconciliationController(ReconciliationRecordRepository reconciliationRecordRepository,
                                     CurrentPrincipal currentPrincipal) {
        this.reconciliationRecordRepository = reconciliationRecordRepository;
        this.currentPrincipal = currentPrincipal;
    }

    public record ReconciliationRecordResponse(String id, String paymentId, long expectedAmountMinorUnits,
                                                Long confirmedAmountMinorUnits, boolean matched,
                                                String discrepancyNote, Instant createdAt) {
    }

    @GetMapping("/api/v1/reconciliation")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ReconciliationRecordResponse> list(@RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "50") int size) {
        Page<ReconciliationRecord> results = reconciliationRecordRepository.findByMerchantId(
                currentPrincipal.merchantId(), PageRequest.of(page, size));
        return results.map(this::toResponse).toList();
    }

    private ReconciliationRecordResponse toResponse(ReconciliationRecord record) {
        return new ReconciliationRecordResponse(record.getId().toString(), record.getPaymentId().toString(),
                record.getExpectedAmountMinorUnits(), record.getConfirmedAmountMinorUnits(), record.isMatched(),
                record.getDiscrepancyNote(), record.getCreatedAt());
    }
}
