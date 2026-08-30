package com.scansettle.api.openbanking;

import com.scansettle.api.openbanking.model.AuthorisationResult;
import com.scansettle.api.openbanking.model.PaymentInstruction;
import com.scansettle.api.openbanking.model.PaymentStatusResult;
import com.scansettle.api.openbanking.model.ProviderPayment;
import com.scansettle.api.openbanking.model.RefundResult;
import com.scansettle.api.openbanking.model.SupportedBank;
import com.scansettle.api.openbanking.model.WebhookProcessingResult;
import com.scansettle.api.openbanking.model.WebhookRequest;

import java.util.List;

/**
 * The single seam between ScanSettle's domain and any regulated Open Banking / PISP
 * provider (docs/open-banking.md). Every type in this interface is a ScanSettle
 * domain type — no vendor SDK type may ever appear here or be referenced by callers
 * of this interface. Provider-specific translation lives entirely inside each
 * adapter (e.g. a future {@code {{OPEN_BANKING_PROVIDER}}Adapter}).
 */
public interface OpenBankingProvider {

    AuthorisationResult createAuthorisation(PaymentInstruction instruction);

    ProviderPayment createPayment(PaymentInstruction instruction);

    PaymentStatusResult getPaymentStatus(String providerReference);

    void cancelPayment(String providerReference);

    /**
     * Not every provider (or Open Banking rail) supports pushing money back out.
     * Callers must check {@link RefundResult#supported()} rather than assuming a
     * refund always succeeds — see ADR-0004 (docs/decisions/0004-refund-scope.md).
     */
    RefundResult refundPayment(String providerReference, long amountMinorUnits);

    WebhookProcessingResult handleWebhook(WebhookRequest request);

    List<SupportedBank> getSupportedBanks();
}
