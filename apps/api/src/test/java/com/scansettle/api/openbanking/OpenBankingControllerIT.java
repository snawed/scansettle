package com.scansettle.api.openbanking;

import com.scansettle.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpenBankingControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void supportedBanksIsPublic_noAuthRequired() throws Exception {
        // Per docs/api.md — the customer bank-selection screen has no authenticated
        // relationship with ScanSettle yet, so this must never require a token.
        mockMvc.perform(get("/api/v1/open-banking/banks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].name").exists());
    }
}
