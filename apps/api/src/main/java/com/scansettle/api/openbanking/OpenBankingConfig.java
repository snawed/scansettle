package com.scansettle.api.openbanking;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Selects which {@link OpenBankingProvider} implementation is wired, driven by
 * {@code app.open-banking.provider} ({@link #OPEN_BANKING_PROVIDER} placeholder).
 * "mock" is the only value implemented in Phase 2/4; Phase 5 adds a real adapter
 * bean guarded the same way, so changing environments is a config change, not a
 * code change (docs/open-banking.md).
 */
@Configuration
public class OpenBankingConfig {

    @Bean
    @ConditionalOnProperty(name = "app.open-banking.provider", havingValue = "mock", matchIfMissing = true)
    public OpenBankingProvider mockOpenBankingProvider(
            ObjectMapper objectMapper,
            @Value("${app.open-banking.webhook-secret}") String webhookSecret,
            @Value("${app.open-banking.mock-bank-base-url}") String mockBankBaseUrl) {
        return new MockOpenBankingProvider(objectMapper, webhookSecret, mockBankBaseUrl);
    }

    // Phase 5 (only once {{OPEN_BANKING_PROVIDER}} is selected and approved):
    //
    // @Bean
    // @ConditionalOnProperty(name = "app.open-banking.provider", havingValue = "{{OPEN_BANKING_PROVIDER}}")
    // public OpenBankingProvider realOpenBankingProvider(
    //         @Value("${app.open-banking.client-id}") String clientId,
    //         @Value("${app.open-banking.client-secret}") String clientSecret,
    //         @Value("${app.open-banking.webhook-secret}") String webhookSecret,
    //         @Value("${app.open-banking.redirect-url}") String redirectUrl) {
    //     return new {{OPEN_BANKING_PROVIDER}}Adapter(clientId, clientSecret, webhookSecret, redirectUrl);
    // }
}
