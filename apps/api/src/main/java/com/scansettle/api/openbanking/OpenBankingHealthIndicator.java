package com.scansettle.api.openbanking;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Surfaces under {@code /actuator/health} as component "openBanking" — proves the
 * configured {@link OpenBankingProvider} bean is reachable, whichever implementation
 * is currently wired.
 */
@Component
public class OpenBankingHealthIndicator implements HealthIndicator {

    private final OpenBankingProvider openBankingProvider;

    public OpenBankingHealthIndicator(OpenBankingProvider openBankingProvider) {
        this.openBankingProvider = openBankingProvider;
    }

    @Override
    public Health health() {
        try {
            int bankCount = openBankingProvider.getSupportedBanks().size();
            return Health.up()
                    .withDetail("provider", openBankingProvider.getClass().getSimpleName())
                    .withDetail("supportedBanks", bankCount)
                    .build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
