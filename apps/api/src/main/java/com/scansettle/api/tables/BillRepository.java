package com.scansettle.api.tables;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface BillRepository extends JpaRepository<Bill, UUID> {

    Optional<Bill> findByIdAndVenueId(UUID id, UUID venueId);

    /** Most recent bill for a table — the "current" one a customer's QR scan should show. */
    Optional<Bill> findFirstByTableIdOrderByOpenedAtDesc(UUID tableId);

    /**
     * Row-locks the bill for the duration of the reservation check-and-insert
     * (docs/scansettle-tables.md) — the entire critical section is one short,
     * no-external-call transaction, so a pessimistic lock is simple and safe at the
     * concurrency level this product actually sees (ADR-0003).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Bill b where b.id = :id")
    Optional<Bill> findByIdForUpdate(@Param("id") UUID id);
}
