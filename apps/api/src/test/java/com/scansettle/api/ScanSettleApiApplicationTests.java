package com.scansettle.api;

import com.scansettle.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

/** Proves the whole wiring — Flyway migrations, JPA, Spring Security, config
 *  binding — comes up cleanly against a real Postgres. */
class ScanSettleApiApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
        // If the Spring context fails to start (bad migration, missing bean,
        // misconfigured security chain, ...), this test fails.
    }
}
