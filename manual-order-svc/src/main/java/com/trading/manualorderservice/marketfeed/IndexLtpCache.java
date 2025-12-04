package com.trading.manualorderservice.marketfeed;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class IndexLtpCache {

    private final Map<String, Double> indexPriceMap = new ConcurrentHashMap<>();

    public void update(String indexName, double ltp) {
        indexPriceMap.put(indexName, ltp);
    }

    public Optional<Double> get(String indexName) {
        return Optional.ofNullable(indexPriceMap.get(indexName));
    }
}

