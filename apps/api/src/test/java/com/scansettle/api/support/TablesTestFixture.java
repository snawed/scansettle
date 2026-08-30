package com.scansettle.api.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sets up a venue, table, and an open bill (Food £48 / Drinks £27 / Dessert £15 = £90 — the Phase 0 worked example). */
public class TablesTestFixture {

    public String venueId;
    public String tableId;
    public String billId;

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    public TablesTestFixture(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    public TablesTestFixture openNinetyPoundBill(MerchantTestFixture merchant) throws Exception {
        String venueResponse = mockMvc.perform(post("/api/v1/venues")
                        .header("Authorization", merchant.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "The Red Lion"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        venueId = objectMapper.readTree(venueResponse).get("id").asText();

        String tableResponse = mockMvc.perform(post("/api/v1/venues/" + venueId + "/tables")
                        .header("Authorization", merchant.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("label", "Table 14"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        tableId = objectMapper.readTree(tableResponse).get("id").asText();

        List<Map<String, Object>> lineItems = List.of(
                Map.of("description", "Food", "amountMinorUnits", 4800),
                Map.of("description", "Drinks", "amountMinorUnits", 2700),
                Map.of("description", "Dessert", "amountMinorUnits", 1500));
        String billResponse = mockMvc.perform(post("/api/v1/tables/" + tableId + "/bill")
                        .header("Authorization", merchant.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("lineItems", lineItems))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode billJson = objectMapper.readTree(billResponse);
        billId = billJson.get("id").asText();

        return this;
    }
}
