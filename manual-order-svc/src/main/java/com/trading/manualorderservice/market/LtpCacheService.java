package com.trading.manualorderservice.market;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class LtpCacheService {

    private final Map<String, Double> ltpMap = new ConcurrentHashMap<>();
    private final Map<String, Long> timestampMap = new ConcurrentHashMap<>();

    // LTP freshness timeout (8 seconds recommended)
    private static final long FRESHNESS_MS = 8000;

    /** Save LTP into cache */
    public void update(String securityId, double ltp) {
        ltpMap.put(securityId, ltp);
        timestampMap.put(securityId, System.currentTimeMillis());
    }

    /** Return LTP only if fresh */
    public Double getFresh(String securityId) {
        Long ts = timestampMap.get(securityId);
        if (ts == null) return null;

        long age = System.currentTimeMillis() - ts;
        if (age > FRESHNESS_MS) return null;

        return ltpMap.get(securityId);
    }

    /** Always return latest cached value (even if stale) */
    public Double getLastKnown(String securityId) {
        return ltpMap.get(securityId);
    }

    public boolean exists(String securityId) {
        return ltpMap.containsKey(securityId);
    }
}
