package com.scansettle.api.tables;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BillLineItemRepository extends JpaRepository<BillLineItem, UUID> {

    List<BillLineItem> findByBillId(UUID billId);

    Optional<BillLineItem> findByIdAndBillId(UUID id, UUID billId);
}
