package com.scansettle.api.tables;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BillPaymentReservationRepository extends JpaRepository<BillPaymentReservation, UUID> {

    Optional<BillPaymentReservation> findByBillPaymentId(UUID billPaymentId);

    @Query("select coalesce(sum(r.requestedAmountMinorUnits), 0) from BillPaymentReservation r " +
            "where r.billId = :billId and r.status = 'ACTIVE'")
    long sumActiveReservations(@Param("billId") UUID billId);

    List<BillPaymentReservation> findByStatusAndExpiresAtBefore(BillPaymentReservation.Status status, Instant cutoff);
}
