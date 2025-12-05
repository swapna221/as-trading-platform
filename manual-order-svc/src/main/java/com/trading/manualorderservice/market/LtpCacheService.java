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

    // FIXED — must be > 20 seconds batch interval
    private static final long FRESHNESS_MS = 30_000; // 30 sec

    private String key(String segment, String secId) {
        return segment + "|" + secId;
    }

    public void update(String segment, String secId, double ltp) {
        String k = key(segment, secId);
        ltpMap.put(k, ltp);
        timestampMap.put(k, System.currentTimeMillis());
    }

    public Double getFresh(String segment, String secId) {
        String k = key(segment, secId);

        Long ts = timestampMap.get(k);
        if (ts == null) return null;

        long age = System.currentTimeMillis() - ts;
        if (age > FRESHNESS_MS) return null;

        return ltpMap.get(k);
    }

    public Double getLastKnown(String segment, String secId) {
        return ltpMap.get(key(segment, secId));
    }

    public boolean exists(String segment, String secId) {
        return ltpMap.containsKey(key(segment, secId));
    }
}
