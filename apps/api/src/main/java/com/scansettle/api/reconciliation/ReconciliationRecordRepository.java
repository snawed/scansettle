package com.scansettle.api.reconciliation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ReconciliationRecordRepository extends JpaRepository<ReconciliationRecord, UUID> {

    @Query("select r from ReconciliationRecord r where r.paymentId in " +
            "(select p.id from Payment p where p.merchantId = :merchantId) order by r.createdAt desc")
    Page<ReconciliationRecord> findByMerchantId(@Param("merchantId") UUID merchantId, Pageable pageable);

    List<ReconciliationRecord> findByPaymentId(UUID paymentId);
}
