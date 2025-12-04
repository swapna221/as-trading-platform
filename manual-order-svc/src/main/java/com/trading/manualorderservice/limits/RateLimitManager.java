package com.trading.manualorderservice.limits;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimitManager {

    /** GLOBAL Bucket — 900 requests per minute allowed */
    private static final int GLOBAL_LIMIT = 900;
    private static final long GLOBAL_REFILL_MS = 60_000;

    private static int globalTokens = GLOBAL_LIMIT;
    private static long lastGlobalRefill = System.currentTimeMillis();

    /** Per-user LTP limit = 1 request/sec */
    private static final Map<Long, UserBucket> userBuckets = new ConcurrentHashMap<>();


    public static synchronized boolean acquireGlobal() {
        refillGlobal();

        if (globalTokens > 0) {
            globalTokens--;
            return true;
        }
        return false;
    }

    private static void refillGlobal() {
        long now = System.currentTimeMillis();
        if (now - lastGlobalRefill >= GLOBAL_REFILL_MS) {
            globalTokens = GLOBAL_LIMIT;
            lastGlobalRefill = now;
        }
    }

    /** Per-user 1 LTP request/sec throttle */
    public static boolean acquireUserLtp(Long userId) {
        userBuckets.putIfAbsent(userId, new UserBucket());

        return userBuckets.get(userId).acquire();
    }


    private static class UserBucket {
        private static final long REFILL_MS = 1000; // 1 sec
        private long last = 0;

        synchronized boolean acquire() {
            long now = Instant.now().toEpochMilli();

            if (now - last >= REFILL_MS) {
                last = now;
                return true;
            }
            return false;
        }
    }
}
