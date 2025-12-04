package com.trading.manualorderservice.limits;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GlobalRateLimitRegistry {

    // last LTP call timestamp per user
    private static final Map<Long, Long> lastLtpCallTime = new ConcurrentHashMap<>();

    // minimum gap 1000 ms (1/sec)
    private static final long MIN_GAP = 1000;

    public static synchronized void awaitTurn(Long userId) {
        long now = System.currentTimeMillis();
        long last = lastLtpCallTime.getOrDefault(userId, 0L);

        long diff = now - last;

        if (diff < MIN_GAP) {
            try {
                Thread.sleep(MIN_GAP - diff);
            } catch (InterruptedException ignored) {}
        }

        lastLtpCallTime.put(userId, System.currentTimeMillis());
    }
}

