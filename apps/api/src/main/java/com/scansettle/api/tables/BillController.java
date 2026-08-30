package com.scansettle.api.tables;

import com.scansettle.api.common.error.ApplicationException;
import com.scansettle.api.common.error.ConflictException;
import com.scansettle.api.common.error.NotFoundException;
import com.scansettle.api.security.CurrentPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class BillController {

    private final BillRepository billRepository;
    private final BillLineItemRepository billLineItemRepository;
    private final BillPaymentRepository billPaymentRepository;
    private final BillPaymentReservationRepository reservationRepository;
    private final DiningTableRepository diningTableRepository;
    private final VenueRepository venueRepository;
    private final CurrentPrincipal currentPrincipal;

    public BillController(BillRepository billRepository, BillLineItemRepository billLineItemRepository,
                           BillPaymentRepository billPaymentRepository,
                           BillPaymentReservationRepository reservationRepository,
                           DiningTableRepository diningTableRepository, VenueRepository venueRepository,
                           CurrentPrincipal currentPrincipal) {
        this.billRepository = billRepository;
        this.billLineItemRepository = billLineItemRepository;
        this.billPaymentRepository = billPaymentRepository;
        this.reservationRepository = reservationRepository;
        this.diningTableRepository = diningTableRepository;
        this.venueRepository = venueRepository;
        this.currentPrincipal = currentPrincipal;
    }

    // --- Merchant: open a bill on a table (manual entry — no POS integration until Phase 7) ---

    public record LineItemRequest(@NotBlank String description, @Positive long amountMinorUnits) {
    }

    public record OpenBillRequest(@NotEmpty List<LineItemRequest> lineItems) {
    }

    public record BillResponse(String id, String venueName, String tableLabel, List<LineItemResponse> lineItems,
                                long totalAmountMinorUnits, long paidAmountMinorUnits, long remainingAmountMinorUnits,
                                String currencyCode, String state) {
    }

    public record LineItemResponse(String id, String description, long amountMinorUnits) {
    }

    public record ItemUpdateRequest(@NotBlank String description, @Positive long amountMinorUnits) {
    }

    @PostMapping("/api/v1/tables/{tableId}/bill")
    @PreAuthorize("hasRole('STAFF')")
    @Transactional
    public ResponseEntity<BillResponse> openBill(@PathVariable UUID tableId, @Valid @RequestBody OpenBillRequest request) {
        DiningTable table = diningTableRepository.findById(tableId)
                .orElseThrow(() -> new NotFoundException("Table not found"));
        Venue venue = venueRepository.findByIdAndMerchantId(table.getVenueId(), currentPrincipal.merchantId())
                .orElseThrow(() -> new NotFoundException("Table not found"));

        billRepository.findFirstByTableIdOrderByOpenedAtDesc(tableId)
                .filter(Bill::isOpenForPayment)
                .ifPresent(existing -> {
                    throw new ConflictException("bill-already-open",
                            "This table already has an open bill — void it before opening a new one.");
                });

        long total = request.lineItems().stream().mapToLong(LineItemRequest::amountMinorUnits).sum();
        Bill bill = new Bill(UUID.randomUUID(), venue.getId(), tableId, total, "GBP");
        billRepository.save(bill);

        for (LineItemRequest item : request.lineItems()) {
            billLineItemRepository.save(new BillLineItem(UUID.randomUUID(), bill.getId(), item.description(),
                    item.amountMinorUnits()));
        }

        table.occupy();
        diningTableRepository.save(table);

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(bill, venue.getName(), table.getLabel()));
    }

    @PostMapping("/api/v1/tables/{tableId}/bill/items")
    @PreAuthorize("hasRole('STAFF')")
    @Transactional
    public BillResponse addItems(@PathVariable UUID tableId, @Valid @RequestBody OpenBillRequest request) {
        DiningTable table = diningTableRepository.findById(tableId)
                .orElseThrow(() -> new NotFoundException("Table not found"));
        Venue venue = venueRepository.findByIdAndMerchantId(table.getVenueId(), currentPrincipal.merchantId())
                .orElseThrow(() -> new NotFoundException("Table not found"));

        UUID openBillId = billRepository.findFirstByTableIdOrderByOpenedAtDesc(tableId)
                .filter(Bill::isOpenForPayment)
                .map(Bill::getId)
                .orElseThrow(() -> new ConflictException("no-open-bill",
                        "This table has no open bill — open one before adding items."));

        // Row-locked so a concurrent "add items" can't lose an update to the running total
        // (same short, no-external-call critical section pattern as the reservation check, ADR-0003).
        Bill bill = billRepository.findByIdForUpdate(openBillId)
                .orElseThrow(() -> new NotFoundException("Bill not found"));

        for (LineItemRequest item : request.lineItems()) {
            billLineItemRepository.save(new BillLineItem(UUID.randomUUID(), bill.getId(), item.description(),
                    item.amountMinorUnits()));
        }
        recalculateTotalFromLineItems(bill);

        return toResponse(bill, venue.getName(), table.getLabel());
    }

    @PatchMapping("/api/v1/bills/{billId}/items/{itemId}")
    @PreAuthorize("hasRole('STAFF')")
    @Transactional
    public BillResponse updateItem(@PathVariable UUID billId, @PathVariable UUID itemId,
                                    @Valid @RequestBody ItemUpdateRequest request) {
        Bill bill = billRepository.findByIdForUpdate(billId).orElseThrow(() -> new NotFoundException("Bill not found"));
        Venue venue = venueRepository.findByIdAndMerchantId(bill.getVenueId(), currentPrincipal.merchantId())
                .orElseThrow(() -> new NotFoundException("Bill not found"));
        if (!bill.isOpenForPayment()) {
            throw new ConflictException("bill-not-open", "This bill is no longer open — items can't be amended.");
        }

        BillLineItem item = billLineItemRepository.findByIdAndBillId(itemId, billId)
                .orElseThrow(() -> new NotFoundException("Line item not found"));
        item.update(request.description(), request.amountMinorUnits());
        billLineItemRepository.save(item);
        recalculateTotalFromLineItems(bill);

        DiningTable table = diningTableRepository.findById(bill.getTableId()).orElse(null);
        return toResponse(bill, venue.getName(), table != null ? table.getLabel() : null);
    }

    @DeleteMapping("/api/v1/bills/{billId}/items/{itemId}")
    @PreAuthorize("hasRole('STAFF')")
    @Transactional
    public BillResponse deleteItem(@PathVariable UUID billId, @PathVariable UUID itemId) {
        Bill bill = billRepository.findByIdForUpdate(billId).orElseThrow(() -> new NotFoundException("Bill not found"));
        Venue venue = venueRepository.findByIdAndMerchantId(bill.getVenueId(), currentPrincipal.merchantId())
                .orElseThrow(() -> new NotFoundException("Bill not found"));
        if (!bill.isOpenForPayment()) {
            throw new ConflictException("bill-not-open", "This bill is no longer open — items can't be amended.");
        }

        List<BillLineItem> items = billLineItemRepository.findByBillId(billId);
        if (items.size() <= 1) {
            throw new ConflictException("last-item",
                    "A bill needs at least one line item — void the bill instead of removing its last item.");
        }
        BillLineItem item = items.stream().filter(li -> li.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> new NotFoundException("Line item not found"));
        billLineItemRepository.delete(item);
        recalculateTotalFromLineItems(bill);

        DiningTable table = diningTableRepository.findById(bill.getTableId()).orElse(null);
        return toResponse(bill, venue.getName(), table != null ? table.getLabel() : null);
    }

    @PostMapping("/api/v1/bills/{billId}/void")
    @PreAuthorize("hasRole('STAFF')")
    @Transactional
    public BillResponse voidBill(@PathVariable UUID billId) {
        Bill bill = billRepository.findById(billId).orElseThrow(() -> new NotFoundException("Bill not found"));
        venueRepository.findByIdAndMerchantId(bill.getVenueId(), currentPrincipal.merchantId())
                .orElseThrow(() -> new ApplicationException(org.springframework.http.HttpStatus.NOT_FOUND,
                        "not-found", "Bill not found"));

        bill.voidBill();
        billRepository.save(bill);

        diningTableRepository.findById(bill.getTableId()).ifPresent(table -> {
            table.free();
            diningTableRepository.save(table);
        });

        return toResponseWithoutNames(bill);
    }

    // --- Public: the customer's QR scan and bill views ---

    public record ScanResponse(String venueName, String tableLabel, String occupancyStatus, BillResponse bill) {
    }

    @GetMapping("/api/v1/tables/scan/{qrToken}")
    public ScanResponse scan(@PathVariable String qrToken) {
        DiningTable table = diningTableRepository.findByQrToken(qrToken)
                .orElseThrow(() -> new NotFoundException("Table not found"));
        Venue venue = venueRepository.findById(table.getVenueId())
                .orElseThrow(() -> new IllegalStateException("Table references a missing Venue"));

        if (table.getOccupancyStatus() == DiningTable.OccupancyStatus.FREE) {
            return new ScanResponse(venue.getName(), table.getLabel(), table.getOccupancyStatus().name(), null);
        }

        Bill bill = billRepository.findFirstByTableIdOrderByOpenedAtDesc(table.getId())
                .filter(Bill::isOpenForPayment)
                .orElseThrow(() -> new NotFoundException("No bill is currently open for this table"));

        return new ScanResponse(venue.getName(), table.getLabel(), table.getOccupancyStatus().name(),
                toResponse(bill, venue.getName(), table.getLabel()));
    }

    @GetMapping("/api/v1/bills/{billId}")
    public BillResponse getBill(@PathVariable UUID billId) {
        Bill bill = billRepository.findById(billId).orElseThrow(() -> new NotFoundException("Bill not found"));
        Venue venue = venueRepository.findById(bill.getVenueId())
                .orElseThrow(() -> new IllegalStateException("Bill references a missing Venue"));
        DiningTable table = diningTableRepository.findById(bill.getTableId())
                .orElseThrow(() -> new IllegalStateException("Bill references a missing Table"));
        return toResponse(bill, venue.getName(), table.getLabel());
    }

    private void recalculateTotalFromLineItems(Bill bill) {
        long total = billLineItemRepository.findByBillId(bill.getId()).stream()
                .mapToLong(BillLineItem::getAmountMinorUnits)
                .sum();
        bill.setTotalAmountMinorUnits(total);
        billRepository.save(bill);
    }

    private BillResponse toResponseWithoutNames(Bill bill) {
        return toResponse(bill, null, null);
    }

    private BillResponse toResponse(Bill bill, String venueName, String tableLabel) {
        List<LineItemResponse> items = billLineItemRepository.findByBillId(bill.getId()).stream()
                .map(li -> new LineItemResponse(li.getId().toString(), li.getDescription(), li.getAmountMinorUnits()))
                .toList();
        long paid = billPaymentRepository.sumConfirmedContribution(bill.getId());
        long activelyReserved = reservationRepository.sumActiveReservations(bill.getId());
        long remaining = bill.getTotalAmountMinorUnits() - paid - activelyReserved;

        return new BillResponse(bill.getId().toString(), venueName, tableLabel, items, bill.getTotalAmountMinorUnits(),
                paid, Math.max(remaining, 0), bill.getCurrencyCode(), bill.getState().name());
    }
}
