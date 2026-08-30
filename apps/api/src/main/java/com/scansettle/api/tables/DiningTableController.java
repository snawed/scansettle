package com.scansettle.api.tables;

import com.scansettle.api.common.error.NotFoundException;
import com.scansettle.api.security.CurrentPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class DiningTableController {

    private final DiningTableRepository diningTableRepository;
    private final VenueRepository venueRepository;
    private final CurrentPrincipal currentPrincipal;

    public DiningTableController(DiningTableRepository diningTableRepository, VenueRepository venueRepository,
                                  CurrentPrincipal currentPrincipal) {
        this.diningTableRepository = diningTableRepository;
        this.venueRepository = venueRepository;
        this.currentPrincipal = currentPrincipal;
    }

    public record CreateTableRequest(@NotBlank String label) {
    }

    public record TableResponse(String id, String label, String qrToken, String status, String occupancyStatus) {
    }

    @PostMapping("/api/v1/venues/{venueId}/tables")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TableResponse> create(@PathVariable UUID venueId, @Valid @RequestBody CreateTableRequest request) {
        venueRepository.findByIdAndMerchantId(venueId, currentPrincipal.merchantId())
                .orElseThrow(() -> new NotFoundException("Venue not found"));

        DiningTable table = new DiningTable(UUID.randomUUID(), venueId, request.label());
        diningTableRepository.save(table);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(table));
    }

    @GetMapping("/api/v1/venues/{venueId}/tables")
    @PreAuthorize("hasRole('READ_ONLY')")
    public List<TableResponse> list(@PathVariable UUID venueId) {
        venueRepository.findByIdAndMerchantId(venueId, currentPrincipal.merchantId())
                .orElseThrow(() -> new NotFoundException("Venue not found"));

        return diningTableRepository.findByVenueId(venueId).stream().map(this::toResponse).toList();
    }

    private TableResponse toResponse(DiningTable table) {
        return new TableResponse(table.getId().toString(), table.getLabel(), table.getQrToken(),
                table.getStatus().name(), table.getOccupancyStatus().name());
    }
}
