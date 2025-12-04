package com.trading.manualorderservice.market;

import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LtpCache {

    private static final long TTL = 5000; // 5 seconds

    private static class Entry {
        double price;
        long timestamp;

        Entry(double price) {
            this.price = price;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    public void put(String segment, String id, double price) {
        cache.put(segment + "|" + id, new Entry(price));
    }

    public Double get(String segment, String id) {
        String key = segment + "|" + id;
        Entry e = cache.get(key);

        if (e == null) return null;

        if (System.currentTimeMillis() - e.timestamp > TTL) {
            cache.remove(key);
            return null;
        }
        return e.price;
    }
}
