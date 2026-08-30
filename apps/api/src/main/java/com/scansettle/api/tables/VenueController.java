package com.scansettle.api.tables;

import com.scansettle.api.security.CurrentPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class VenueController {

    private final VenueRepository venueRepository;
    private final CurrentPrincipal currentPrincipal;

    public VenueController(VenueRepository venueRepository, CurrentPrincipal currentPrincipal) {
        this.venueRepository = venueRepository;
        this.currentPrincipal = currentPrincipal;
    }

    public record CreateVenueRequest(@NotBlank String name, String address, String timezone) {
    }

    public record VenueResponse(String id, String name, String address, String timezone) {
    }

    @PostMapping("/api/v1/venues")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VenueResponse> create(@Valid @RequestBody CreateVenueRequest request) {
        Venue venue = new Venue(UUID.randomUUID(), currentPrincipal.merchantId(), request.name(), request.address(),
                request.timezone());
        venueRepository.save(venue);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(venue));
    }

    @GetMapping("/api/v1/venues")
    @PreAuthorize("hasRole('READ_ONLY')")
    public List<VenueResponse> list() {
        return venueRepository.findByMerchantId(currentPrincipal.merchantId()).stream().map(this::toResponse).toList();
    }

    private VenueResponse toResponse(Venue venue) {
        return new VenueResponse(venue.getId().toString(), venue.getName(), venue.getAddress(), venue.getTimezone());
    }
}
