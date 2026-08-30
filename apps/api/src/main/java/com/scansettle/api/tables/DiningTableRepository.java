package com.scansettle.api.tables;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiningTableRepository extends JpaRepository<DiningTable, UUID> {

    List<DiningTable> findByVenueId(UUID venueId);

    Optional<DiningTable> findByIdAndVenueId(UUID id, UUID venueId);

    Optional<DiningTable> findByQrToken(String qrToken);
}
