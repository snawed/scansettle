package com.scansettle.api.fraud;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * A generic in-process sliding-window hit counter, keyed by any string. No new
 * infra, matching the RateLimiter's own MVP scope — single-instance only, and the
 * per-key deque never shrinks its map entry back to zero (acceptable at MVP scale;
 * a multi-instance deployment or long-running memory growth would need a shared
 * store instead, same known limit as RateLimiter).
 */
@Component
public class VelocityTracker {

    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<Long>> hits = new ConcurrentHashMap<>();

    /** Records a hit for {@code key} now, then returns how many hits (including this
     *  one) fall within the trailing {@code windowSeconds}. */
    public int recordAndCount(String key, long windowSeconds) {
        long now = System.currentTimeMillis();
        long cutoff = now - windowSeconds * 1000;
        ConcurrentLinkedDeque<Long> timestamps = hits.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        timestamps.addLast(now);

        Long oldest;
        while ((oldest = timestamps.peekFirst()) != null && oldest < cutoff) {
            timestamps.pollFirst();
        }
        return timestamps.size();
    }
}
