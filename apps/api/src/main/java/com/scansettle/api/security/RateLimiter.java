package com.scansettle.api.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * A minimal in-process token bucket, one per key (typically client IP) — no new
 * infra, matching architecture.md's Section 12 decision for MVP rate limiting.
 * Single-instance only: a multi-instance deployment would need a shared store
 * (Redis, Postgres) instead, called out as a known limit rather than built now.
 */
@Component
public class RateLimiter {

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final double capacity;
    private final double refillPerSecond;

    public RateLimiter(@Value("${app.rate-limit.capacity:60}") double capacity,
                        @Value("${app.rate-limit.refill-per-second:1.0}") double refillPerSecond) {
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
    }

    public boolean tryConsume(String key) {
        return buckets.computeIfAbsent(key, k -> new Bucket(capacity)).tryConsume(refillPerSecond, capacity);
    }

    private static final class Bucket {
        private double tokens;
        private long lastRefillNanos;

        Bucket(double initialTokens) {
            this.tokens = initialTokens;
            this.lastRefillNanos = System.nanoTime();
        }

        synchronized boolean tryConsume(double refillPerSecond, double capacity) {
            long now = System.nanoTime();
            double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
            tokens = Math.min(capacity, tokens + elapsedSeconds * refillPerSecond);
            lastRefillNanos = now;
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }
    }
}
