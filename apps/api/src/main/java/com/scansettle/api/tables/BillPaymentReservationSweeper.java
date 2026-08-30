package com.scansettle.api.tables;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Frees the amount a customer reserved but never completed paying — e.g. they
 * abandoned the bank authentication step (docs/scansettle-tables.md). Runs
 * frequently since the reservation TTL itself is short (10 minutes).
 */
@Component
public class BillPaymentReservationSweeper {

    private static final Logger log = LoggerFactory.getLogger(BillPaymentReservationSweeper.class);

    private final BillPaymentReservationRepository reservationRepository;
    private final BillPaymentService billPaymentService;

    public BillPaymentReservationSweeper(BillPaymentReservationRepository reservationRepository,
                                          BillPaymentService billPaymentService) {
        this.reservationRepository = reservationRepository;
        this.billPaymentService = billPaymentService;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void expireAbandonedReservations() {
        var expired = reservationRepository.findByStatusAndExpiresAtBefore(
                BillPaymentReservation.Status.ACTIVE, Instant.now());
        for (var reservation : expired) {
            billPaymentService.expireReservation(reservation);
            log.info("Expired abandoned bill payment reservation [id={}, billId={}, amount={}]",
                    reservation.getId(), reservation.getBillId(), reservation.getRequestedAmountMinorUnits());
        }
    }
}
