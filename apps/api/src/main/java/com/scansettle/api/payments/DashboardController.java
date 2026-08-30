package com.scansettle.api.payments;

import com.scansettle.api.security.CurrentPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Backs the merchant dashboard's summary stat row. Uses UTC day/month boundaries for
 * MVP — should move to the merchant's own timezone once Venue (Phase 6) carries one;
 * flagged as a known simplification, not a silent gap.
 */
@RestController
public class DashboardController {

    private final PaymentRepository paymentRepository;
    private final FeeLedgerEntryRepository feeLedgerEntryRepository;
    private final CurrentPrincipal currentPrincipal;

    public DashboardController(PaymentRepository paymentRepository, FeeLedgerEntryRepository feeLedgerEntryRepository,
                                CurrentPrincipal currentPrincipal) {
        this.paymentRepository = paymentRepository;
        this.feeLedgerEntryRepository = feeLedgerEntryRepository;
        this.currentPrincipal = currentPrincipal;
    }

    public record DashboardSummaryResponse(
            long todayConfirmedAmountMinorUnits, long todayConfirmedCount,
            long monthConfirmedAmountMinorUnits, long monthFeesMinorUnits, long pendingCount) {
    }

    private static final List<PaymentState> TERMINAL_STATES = List.of(
            PaymentState.PAYMENT_CONFIRMED, PaymentState.FAILED, PaymentState.REJECTED,
            PaymentState.CANCELLED, PaymentState.EXPIRED);

    @GetMapping("/api/v1/dashboard/summary")
    @PreAuthorize("hasRole('READ_ONLY')")
    public DashboardSummaryResponse summary() {
        var merchantId = currentPrincipal.merchantId();
        var todayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        var monthStart = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        long todayAmount = paymentRepository.sumConfirmedAmountSince(merchantId, todayStart);
        long todayCount = paymentRepository.countByMerchantIdAndStateAndCreatedAtGreaterThanEqual(
                merchantId, PaymentState.PAYMENT_CONFIRMED, todayStart);
        long monthAmount = paymentRepository.sumConfirmedAmountSince(merchantId, monthStart);
        long monthFees = feeLedgerEntryRepository.sumFeesSince(merchantId, monthStart);
        long pending = paymentRepository.countByMerchantIdAndStateNotInAndCreatedAtGreaterThanEqual(
                merchantId, TERMINAL_STATES, monthStart);

        return new DashboardSummaryResponse(todayAmount, todayCount, monthAmount, monthFees, pending);
    }
}
