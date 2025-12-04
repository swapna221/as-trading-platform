package com.trading.manualorderservice.instrument;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InstrumentRegistry {
    private final InstrumentCache cache;

    public String requireSecurityId(String symbol) {
        String id = cache.getStockId(symbol);
        if (id == null)
            throw new IllegalArgumentException("Unknown/unsupported symbol for NSE EQ: " + symbol);
        return id;
    }
}
