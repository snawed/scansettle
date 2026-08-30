package com.scansettle.api.tables;

import com.scansettle.api.payments.Payment;
import com.scansettle.api.payments.PaymentOutcomeListener;
import com.scansettle.api.payments.PaymentState;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reacts to a Payment reaching a terminal state by committing or releasing the
 * {@link BillPaymentReservation} it came from, and recomputing the {@link Bill}'s
 * state from the true committed total (docs/scansettle-tables.md). No-ops for
 * payments that aren't bill payments (link-based payments have no billPaymentId).
 */
@Component
public class BillPaymentOutcomeListener implements PaymentOutcomeListener {

    private final BillPaymentRepository billPaymentRepository;
    private final BillPaymentReservationRepository reservationRepository;
    private final BillRepository billRepository;
    private final DiningTableRepository diningTableRepository;

    public BillPaymentOutcomeListener(BillPaymentRepository billPaymentRepository,
                                       BillPaymentReservationRepository reservationRepository,
                                       BillRepository billRepository,
                                       DiningTableRepository diningTableRepository) {
        this.billPaymentRepository = billPaymentRepository;
        this.reservationRepository = reservationRepository;
        this.billRepository = billRepository;
        this.diningTableRepository = diningTableRepository;
    }

    @Override
    @Transactional
    public void onPaymentReachedTerminalState(Payment payment) {
        if (payment.getBillPaymentId() == null) {
            return;
        }

        BillPayment billPayment = billPaymentRepository.findById(payment.getBillPaymentId())
                .orElseThrow(() -> new IllegalStateException(
                        "Payment references a missing BillPayment: " + payment.getBillPaymentId()));
        BillPaymentReservation reservation = reservationRepository.findByBillPaymentId(billPayment.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "BillPayment has no reservation: " + billPayment.getId()));

        if (payment.getState() == PaymentState.PAYMENT_CONFIRMED) {
            billPayment.markConfirmed();
            reservation.commit();
        } else {
            billPayment.markFailed();
            reservation.release();
        }
        billPaymentRepository.save(billPayment);
        reservationRepository.save(reservation);

        Bill bill = billRepository.findById(billPayment.getBillId())
                .orElseThrow(() -> new IllegalStateException("BillPayment references a missing Bill"));
        long committed = billPaymentRepository.sumConfirmedContribution(bill.getId());
        bill.reflectCommittedTotal(committed);
        billRepository.save(bill);

        if (bill.getState() == BillState.PAID) {
            diningTableRepository.findById(bill.getTableId()).ifPresent(table -> {
                table.free();
                diningTableRepository.save(table);
            });
        }
    }
}
