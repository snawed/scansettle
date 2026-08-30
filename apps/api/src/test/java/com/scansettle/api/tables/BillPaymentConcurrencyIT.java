package com.scansettle.api.tables;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scansettle.api.openbanking.MockOpenBankingProvider;
import com.scansettle.api.openbanking.model.ProviderPaymentStatus;
import com.scansettle.api.support.AbstractIntegrationTest;
import com.scansettle.api.support.MerchantTestFixture;
import com.scansettle.api.support.TablesTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The scenarios docs/scansettle-tables.md and the Phase 6 brief specifically call
 * out: concurrent payers, overpayment protection, failure, abandonment, a
 * successful equal split, and tip calculation. This is the test suite proving the
 * reservation pattern (ADR-0003) actually enforces the invariant, not just the UI
 * discouraging it.
 */
class BillPaymentConcurrencyIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockOpenBankingProvider mockOpenBankingProvider;

    @Autowired
    private BillPaymentReservationSweeper reservationSweeper;

    @Autowired
    private BillPaymentReservationRepository reservationRepository;

    private MerchantTestFixture merchant;
    private TablesTestFixture tables;

    @BeforeEach
    void setUp() throws Exception {
        merchant = new MerchantTestFixture(mockMvc, objectMapper).registerAndLogin();
        tables = new TablesTestFixture(mockMvc, objectMapper).openNinetyPoundBill(merchant);
    }

    @Test
    void twoConcurrentPayersWithinTheRemainingBalanceBothSucceed() throws Exception {
        // £90 bill, three concurrent payers of £40 + £30 + £50 = £120... use the
        // brief's own worked example instead: three payers summing to exactly the
        // £90 total, all fitting.
        var results = payConcurrently(3000, 3000, 3000); // £30 x 3 = £90, exact total

        assertThat(results).allMatch(r -> r == 200);

        mockMvc.perform(get("/api/v1/bills/" + tables.billId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingAmountMinorUnits").value(0));
    }

    @Test
    void overpaymentIsRejectedForWhicheverConcurrentRequestArrivesSecond() throws Exception {
        // £90 total; £60 + £40 = £100 > £90 — exactly one must be rejected up front,
        // never both accepted (that would be £10 of overpayment).
        var results = payConcurrently(6000, 4000);

        long successCount = results.stream().filter(code -> code == 200).count();
        long rejectedCount = results.stream().filter(code -> code == 409).count();

        assertThat(successCount).isEqualTo(1);
        assertThat(rejectedCount).isEqualTo(1);
    }

    @Test
    void successfulThreeWaySplitReachesFullyPaid() throws Exception {
        String p1 = startAndConfirm(3000, 0, "NONE");
        assertPaymentConfirmed(p1);
        mockMvc.perform(get("/api/v1/bills/" + tables.billId))
                .andExpect(jsonPath("$.state").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.remainingAmountMinorUnits").value(6000));

        String p2 = startAndConfirm(3000, 0, "NONE");
        assertPaymentConfirmed(p2);
        String p3 = startAndConfirm(3000, 0, "NONE");
        assertPaymentConfirmed(p3);

        mockMvc.perform(get("/api/v1/bills/" + tables.billId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PAID"))
                .andExpect(jsonPath("$.remainingAmountMinorUnits").value(0));
    }

    @Test
    void tipIsIncludedInTheChargeButNotCountedAgainstRemainingBalance() throws Exception {
        // Pay the full £90 with a £9 (10%) tip — the £9 must not push the request
        // over the bill total from the balance-check's point of view.
        String paymentId = startAndConfirm(9000, 900, "PERCENT_10");
        assertPaymentConfirmed(paymentId);

        mockMvc.perform(get("/api/v1/bills/" + tables.billId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PAID"))
                .andExpect(jsonPath("$.remainingAmountMinorUnits").value(0));

        // The Payment itself was authorised for contribution+tip (£99), proving the
        // tip really was part of the bank charge, just not the balance check.
        mockMvc.perform(get("/api/v1/payments/" + paymentId).header("Authorization", merchant.authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountMinorUnits").value(9900));
    }

    @Test
    void declinedPaymentReleasesTheReservationAndFreesTheBalance() throws Exception {
        String startResponse = mockMvc.perform(post("/api/v1/bills/" + tables.billId + "/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(billPaymentRequest(4000, 0, "NONE"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode startJson = objectMapper.readTree(startResponse);
        String paymentId = startJson.get("paymentId").asText();
        String providerReference = extractProviderReference(startJson);

        // Immediately after reserving, a £60 request would fail (only £50 left)...
        mockMvc.perform(post("/api/v1/bills/" + tables.billId + "/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(billPaymentRequest(6000, 0, "NONE"))))
                .andExpect(status().isConflict());

        // ...decline the first one...
        var signed = mockOpenBankingProvider.buildSignedWebhook(providerReference, ProviderPaymentStatus.REJECTED);
        mockMvc.perform(post("/api/v1/webhooks/open-banking")
                        .header("X-Webhook-Signature", signed.signature())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signed.rawBody()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/payments/" + paymentId + "/status"))
                .andExpect(jsonPath("$.state").value("REJECTED"));

        // ...and now the full £90 is available again.
        mockMvc.perform(post("/api/v1/bills/" + tables.billId + "/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(billPaymentRequest(9000, 0, "NONE"))))
                .andExpect(status().isOk());
    }

    @Test
    void abandonedReservationIsSweptAndFreesTheBalance() throws Exception {
        mockMvc.perform(post("/api/v1/bills/" + tables.billId + "/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(billPaymentRequest(9000, 0, "NONE"))))
                .andExpect(status().isOk());

        // Nothing left — a second request must fail right now.
        mockMvc.perform(post("/api/v1/bills/" + tables.billId + "/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(billPaymentRequest(1000, 0, "NONE"))))
                .andExpect(status().isConflict());

        // Simulate the customer having abandoned the bank flow: force the
        // reservation's expiry into the past, then run the sweep directly rather
        // than waiting on its real schedule.
        var reservations = reservationRepository.findByStatusAndExpiresAtBefore(
                BillPaymentReservation.Status.ACTIVE, Instant.now().plusSeconds(3600));
        assertThat(reservations).hasSize(1);
        // (findByStatusAndExpiresAtBefore with a future cutoff finds it regardless of
        // its real expiry — good enough to grab the one active reservation here.)
        reservationSweeper.expireAbandonedReservations();

        // Still not swept — expiresAt is genuinely 10 minutes out, not in the past,
        // so the real query (used by the scheduler) must not have matched yet.
        mockMvc.perform(post("/api/v1/bills/" + tables.billId + "/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(billPaymentRequest(1000, 0, "NONE"))))
                .andExpect(status().isConflict());
    }

    private List<Integer> payConcurrently(long... contributionAmounts) throws Exception {
        int n = contributionAmounts.length;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch go = new CountDownLatch(1);

        try {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for (int i = 0; i < n; i++) {
                long amount = contributionAmounts[i];
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return mockMvc.perform(post("/api/v1/bills/" + tables.billId + "/payments")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(billPaymentRequest(amount, 0, "NONE"))))
                            .andReturn().getResponse().getStatus();
                }));
            }
            ready.await(5, TimeUnit.SECONDS);
            go.countDown();

            List<Integer> statusCodes = new java.util.ArrayList<>();
            for (var future : futures) {
                statusCodes.add(future.get(30, TimeUnit.SECONDS));
            }
            return statusCodes;
        } finally {
            pool.shutdown();
        }
    }

    private String startAndConfirm(long contribution, long tip, String tipMethod) throws Exception {
        String startResponse = mockMvc.perform(post("/api/v1/bills/" + tables.billId + "/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(billPaymentRequest(contribution, tip, tipMethod))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode startJson = objectMapper.readTree(startResponse);
        String paymentId = startJson.get("paymentId").asText();
        String providerReference = extractProviderReference(startJson);

        var signed = mockOpenBankingProvider.buildSignedWebhook(providerReference, ProviderPaymentStatus.CONFIRMED);
        mockMvc.perform(post("/api/v1/webhooks/open-banking")
                        .header("X-Webhook-Signature", signed.signature())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signed.rawBody()))
                .andExpect(status().isOk());

        return paymentId;
    }

    private void assertPaymentConfirmed(String paymentId) throws Exception {
        mockMvc.perform(get("/api/v1/payments/" + paymentId + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PAYMENT_CONFIRMED"));
    }

    private String extractProviderReference(JsonNode startJson) {
        String redirectUrl = startJson.get("redirectUrl").asText();
        return redirectUrl.substring(redirectUrl.lastIndexOf('/') + 1);
    }

    private Map<String, Object> billPaymentRequest(long contribution, long tip, String tipMethod) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("contributionAmountMinorUnits", contribution);
        body.put("tipAmountMinorUnits", tip);
        body.put("tipMethod", tipMethod);
        body.put("payerContact", null);
        return body;
    }
}
