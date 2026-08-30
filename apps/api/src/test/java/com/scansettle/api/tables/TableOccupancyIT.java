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

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Table occupancy — like a real POS, a table is OCCUPIED while a bill is open and
 * flips back to FREE once the bill is settled (paid or voided), at which point a QR
 * scan must stop serving the old bill's numbers.
 */
class TableOccupancyIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockOpenBankingProvider mockOpenBankingProvider;

    private MerchantTestFixture merchant;
    private TablesTestFixture tables;

    @BeforeEach
    void setUp() throws Exception {
        merchant = new MerchantTestFixture(mockMvc, objectMapper).registerAndLogin();
        tables = new TablesTestFixture(mockMvc, objectMapper).openNinetyPoundBill(merchant);
    }

    @Test
    void openingABillOccupiesTheTableAndScanReturnsIt() throws Exception {
        mockMvc.perform(get("/api/v1/venues/" + tables.venueId + "/tables").header("Authorization", merchant.authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].occupancyStatus").value("OCCUPIED"));

        String qrToken = tableQrToken();
        mockMvc.perform(get("/api/v1/tables/scan/" + qrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occupancyStatus").value("OCCUPIED"))
                .andExpect(jsonPath("$.bill.id").value(tables.billId));
    }

    @Test
    void fullyPayingTheBillFreesTheTableAndScanStopsServingTheOldBill() throws Exception {
        payInFull();

        mockMvc.perform(get("/api/v1/venues/" + tables.venueId + "/tables").header("Authorization", merchant.authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].occupancyStatus").value("FREE"));

        String qrToken = tableQrToken();
        mockMvc.perform(get("/api/v1/tables/scan/" + qrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occupancyStatus").value("FREE"))
                .andExpect(jsonPath("$.bill").doesNotExist());
    }

    @Test
    void voidingTheBillFreesTheTable() throws Exception {
        mockMvc.perform(post("/api/v1/bills/" + tables.billId + "/void").header("Authorization", merchant.authHeader()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/venues/" + tables.venueId + "/tables").header("Authorization", merchant.authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].occupancyStatus").value("FREE"));

        String qrToken = tableQrToken();
        mockMvc.perform(get("/api/v1/tables/scan/" + qrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occupancyStatus").value("FREE"))
                .andExpect(jsonPath("$.bill").doesNotExist());
    }

    @Test
    void aNewBillCanBeOpenedOnAFreedTableAndReoccupiesIt() throws Exception {
        payInFull();

        List<Map<String, Object>> lineItems = List.of(Map.of("description", "Round 2", "amountMinorUnits", 2000));
        String billResponse = mockMvc.perform(post("/api/v1/tables/" + tables.tableId + "/bill")
                        .header("Authorization", merchant.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("lineItems", lineItems))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String newBillId = objectMapper.readTree(billResponse).get("id").asText();

        mockMvc.perform(get("/api/v1/venues/" + tables.venueId + "/tables").header("Authorization", merchant.authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].occupancyStatus").value("OCCUPIED"));

        String qrToken = tableQrToken();
        mockMvc.perform(get("/api/v1/tables/scan/" + qrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occupancyStatus").value("OCCUPIED"))
                .andExpect(jsonPath("$.bill.id").value(newBillId));
    }

    @Test
    void itemsCanBeAddedToARunningBillWhileTheTableStaysOccupied() throws Exception {
        List<Map<String, Object>> extraItems = List.of(Map.of("description", "Extra Round", "amountMinorUnits", 1500));
        mockMvc.perform(post("/api/v1/tables/" + tables.tableId + "/bill/items")
                        .header("Authorization", merchant.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("lineItems", extraItems))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(tables.billId))
                .andExpect(jsonPath("$.totalAmountMinorUnits").value(10500))
                .andExpect(jsonPath("$.remainingAmountMinorUnits").value(10500))
                .andExpect(jsonPath("$.lineItems.length()").value(4));

        mockMvc.perform(get("/api/v1/venues/" + tables.venueId + "/tables").header("Authorization", merchant.authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].occupancyStatus").value("OCCUPIED"));
    }

    @Test
    void addingItemsToAFreeTableWithNoOpenBillIsRejected() throws Exception {
        payInFull();

        List<Map<String, Object>> extraItems = List.of(Map.of("description", "Too Late", "amountMinorUnits", 500));
        mockMvc.perform(post("/api/v1/tables/" + tables.tableId + "/bill/items")
                        .header("Authorization", merchant.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("lineItems", extraItems))))
                .andExpect(status().isConflict());
    }

    @Test
    void anExistingItemCanBeAmendedAndTheBillTotalRecalculates() throws Exception {
        String foodItemId = lineItemId(tables.billId, "Food");

        mockMvc.perform(patch("/api/v1/bills/" + tables.billId + "/items/" + foodItemId)
                        .header("Authorization", merchant.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("description", "Food (corrected)", "amountMinorUnits", 6000))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmountMinorUnits").value(10200)) // was 4800, now 6000 -> +1200 on a 9000 total
                .andExpect(jsonPath("$.remainingAmountMinorUnits").value(10200));

        mockMvc.perform(get("/api/v1/bills/" + tables.billId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lineItems[?(@.description == 'Food (corrected)')].amountMinorUnits").value(6000));
    }

    @Test
    void anItemCanBeRemovedButNotTheLastOneRemaining() throws Exception {
        String foodItemId = lineItemId(tables.billId, "Food");
        String drinksItemId = lineItemId(tables.billId, "Drinks");
        String dessertItemId = lineItemId(tables.billId, "Dessert");

        mockMvc.perform(delete("/api/v1/bills/" + tables.billId + "/items/" + foodItemId)
                        .header("Authorization", merchant.authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmountMinorUnits").value(4200)) // 9000 - 4800
                .andExpect(jsonPath("$.lineItems.length()").value(2));

        mockMvc.perform(delete("/api/v1/bills/" + tables.billId + "/items/" + drinksItemId)
                        .header("Authorization", merchant.authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmountMinorUnits").value(1500)) // 4200 - 2700
                .andExpect(jsonPath("$.lineItems.length()").value(1));

        // Only Dessert left — deleting it would leave a bill with no items at all.
        mockMvc.perform(delete("/api/v1/bills/" + tables.billId + "/items/" + dessertItemId)
                        .header("Authorization", merchant.authHeader()))
                .andExpect(status().isConflict());
    }

    @Test
    void itemsCannotBeAmendedOrRemovedOnceTheBillIsSettled() throws Exception {
        String foodItemId = lineItemId(tables.billId, "Food");
        payInFull();

        mockMvc.perform(patch("/api/v1/bills/" + tables.billId + "/items/" + foodItemId)
                        .header("Authorization", merchant.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("description", "Food", "amountMinorUnits", 100))))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/v1/bills/" + tables.billId + "/items/" + foodItemId)
                        .header("Authorization", merchant.authHeader()))
                .andExpect(status().isConflict());
    }

    private String lineItemId(String billId, String description) throws Exception {
        String response = mockMvc.perform(get("/api/v1/bills/" + billId))
                .andReturn().getResponse().getContentAsString();
        JsonNode lineItems = objectMapper.readTree(response).get("lineItems");
        for (JsonNode item : lineItems) {
            if (item.get("description").asText().equals(description)) {
                return item.get("id").asText();
            }
        }
        throw new AssertionError("No line item named '" + description + "' found on bill " + billId);
    }

    private void payInFull() throws Exception {
        String startResponse = mockMvc.perform(post("/api/v1/bills/" + tables.billId + "/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(billPaymentRequest(9000))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode startJson = objectMapper.readTree(startResponse);
        String redirectUrl = startJson.get("redirectUrl").asText();
        String providerReference = redirectUrl.substring(redirectUrl.lastIndexOf('/') + 1);

        var signed = mockOpenBankingProvider.buildSignedWebhook(providerReference, ProviderPaymentStatus.CONFIRMED);
        mockMvc.perform(post("/api/v1/webhooks/open-banking")
                        .header("X-Webhook-Signature", signed.signature())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signed.rawBody()))
                .andExpect(status().isOk());
    }

    private String tableQrToken() throws Exception {
        String response = mockMvc.perform(get("/api/v1/venues/" + tables.venueId + "/tables").header("Authorization", merchant.authHeader()))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get(0).get("qrToken").asText();
    }

    private Map<String, Object> billPaymentRequest(long contribution) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("contributionAmountMinorUnits", contribution);
        body.put("tipAmountMinorUnits", 0);
        body.put("tipMethod", "NONE");
        body.put("payerContact", null);
        return body;
    }
}
