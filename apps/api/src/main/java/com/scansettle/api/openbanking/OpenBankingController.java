package com.scansettle.api.openbanking;

import com.scansettle.api.openbanking.model.SupportedBank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public, no-auth per docs/api.md — the customer bank-selection screen needs this
 * before the customer has any authenticated relationship with ScanSettle.
 */
@RestController
public class OpenBankingController {

    private final OpenBankingProvider openBankingProvider;

    public OpenBankingController(OpenBankingProvider openBankingProvider) {
        this.openBankingProvider = openBankingProvider;
    }

    @GetMapping("/api/v1/open-banking/banks")
    public List<SupportedBank> getSupportedBanks() {
        return openBankingProvider.getSupportedBanks();
    }
}
