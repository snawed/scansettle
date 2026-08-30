package com.scansettle.api.payments;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface FeeLedgerEntryRepository extends JpaRepository<FeeLedgerEntry, UUID> {

    @Query("select coalesce(sum(f.calculatedFeeMinorUnits), 0) from FeeLedgerEntry f " +
            "where f.merchantId = :merchantId and f.createdAt >= :since")
    long sumFeesSince(@Param("merchantId") UUID merchantId, @Param("since") Instant since);
}
