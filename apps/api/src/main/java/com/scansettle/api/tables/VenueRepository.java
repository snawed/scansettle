package com.scansettle.api.tables;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VenueRepository extends JpaRepository<Venue, UUID> {

    List<Venue> findByMerchantId(UUID merchantId);

    Optional<Venue> findByIdAndMerchantId(UUID id, UUID merchantId);
}
