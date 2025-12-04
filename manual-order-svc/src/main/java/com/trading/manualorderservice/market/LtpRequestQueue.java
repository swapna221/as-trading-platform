package com.trading.manualorderservice.market;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LtpRequestQueue {

    private final Map<String, Set<String>> queue = new ConcurrentHashMap<>();

    public void request(String segment, String id) {
        queue.computeIfAbsent(segment, x -> ConcurrentHashMap.newKeySet()).add(id);
    }

    public Map<String, Set<String>> drain() {
        Map<String, Set<String>> copy = new HashMap<>(queue);
        queue.clear();
        return copy;
    }
}
