package com.scansettle.api.tables;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BillPaymentRepository extends JpaRepository<BillPayment, UUID> {

    List<BillPayment> findByBillId(UUID billId);

    @Query("select coalesce(sum(bp.contributionAmountMinorUnits), 0) from BillPayment bp " +
            "where bp.billId = :billId and bp.state = 'CONFIRMED'")
    long sumConfirmedContribution(@Param("billId") UUID billId);
}
