package com.scansettle.api.fraud;

import com.scansettle.api.admin.FraudFlag;
import com.scansettle.api.admin.FraudFlagRepository;
import com.scansettle.api.audit.AuditEvent;
import com.scansettle.api.audit.AuditService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "MVP: basic thresholds + alerting" (docs/security.md, Fraud/velocity controls) —
 * per-payer(IP) and per-merchant payment-creation velocity. Deliberately never
 * blocks a payment attempt: a false positive here would be a declined legitimate
 * customer, so it only raises a {@link FraudFlag} for ops to review
 * ({@code raisedBy} null — see FraudFlag's doc comment), same tooling Phase 8 built
 * for manual flags. Called from the two public payment-creation endpoints
 * (PaymentController, BillPaymentController) after the attempt is recorded, so it
 * never sits on the customer's payment latency path in a blocking way.
 */
@Component
public class PaymentVelocityGuard {

    private final long windowSeconds;
    private final int perIpThreshold;
    private final int perMerchantThreshold;
    private final long cooldownSeconds;

    private final VelocityTracker velocityTracker;
    private final FraudFlagRepository fraudFlagRepository;
    private final AuditService auditService;
    private final boolean enabled;
    private final ConcurrentHashMap<String, Instant> lastFlaggedAt = new ConcurrentHashMap<>();

    public PaymentVelocityGuard(VelocityTracker velocityTracker, FraudFlagRepository fraudFlagRepository,
                                 AuditService auditService,
                                 @Value("${app.velocity-guard.enabled:true}") boolean enabled,
                                 @Value("${app.velocity-guard.window-seconds:300}") long windowSeconds,
                                 @Value("${app.velocity-guard.per-ip-threshold:10}") int perIpThreshold,
                                 @Value("${app.velocity-guard.per-merchant-threshold:30}") int perMerchantThreshold,
                                 @Value("${app.velocity-guard.cooldown-seconds:3600}") long cooldownSeconds) {
        this.velocityTracker = velocityTracker;
        this.fraudFlagRepository = fraudFlagRepository;
        this.auditService = auditService;
        this.enabled = enabled;
        this.windowSeconds = windowSeconds;
        this.perIpThreshold = perIpThreshold;
        this.perMerchantThreshold = perMerchantThreshold;
        this.cooldownSeconds = cooldownSeconds;
    }

    /** Disabled under the test profile (application-test.yml) — same rationale as
     *  RateLimitFilter: integration tests fire many rapid payment attempts from one
     *  loopback address across a shared Spring context, which isn't a real velocity
     *  signal. */
    public void checkAndFlag(UUID merchantId, String clientIp) {
        if (!enabled) {
            return;
        }
        int perIp = velocityTracker.recordAndCount("ip:" + clientIp, windowSeconds);
        if (perIp > perIpThreshold) {
            raiseIfNotCoolingDown("ip:" + clientIp, merchantId,
                    "Unusual payment velocity from a single source: " + perIp + " attempts in 5 minutes (IP " + clientIp + ")");
        }

        int perMerchant = velocityTracker.recordAndCount("merchant:" + merchantId, windowSeconds);
        if (perMerchant > perMerchantThreshold) {
            raiseIfNotCoolingDown("merchant:" + merchantId, merchantId,
                    "Unusual payment velocity for this merchant: " + perMerchant + " attempts in 5 minutes");
        }
    }

    private void raiseIfNotCoolingDown(String cooldownKey, UUID merchantId, String reason) {
        Instant now = Instant.now();
        Instant last = lastFlaggedAt.get(cooldownKey);
        if (last != null && now.minusSeconds(cooldownSeconds).isBefore(last)) {
            return;
        }
        lastFlaggedAt.put(cooldownKey, now);

        FraudFlag flag = new FraudFlag(UUID.randomUUID(), merchantId, null, reason, null);
        fraudFlagRepository.save(flag);
        auditService.record(merchantId, AuditEvent.ActorType.SYSTEM, "velocity-guard", "FRAUD_FLAG_RAISED",
                "FraudFlag", flag.getId().toString(), null, null);
    }
}
