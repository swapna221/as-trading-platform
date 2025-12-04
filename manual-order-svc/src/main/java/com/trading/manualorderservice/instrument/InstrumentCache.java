package com.trading.manualorderservice.instrument;

import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InstrumentCache {
    // symbol -> securityId (only NSE EQ stocks per your requirement)
    private final Map<String, String> stockMap = new ConcurrentHashMap<>();

    public void putStock(String symbol, String securityId) {
        if (symbol != null && securityId != null)
            stockMap.put(symbol.trim().toUpperCase(), securityId.trim());
    }

    public String getStockId(String symbol) {
        if (symbol == null) return null;
        return stockMap.get(symbol.trim().toUpperCase());
    }

    public int size() { return stockMap.size(); }

    public void clear() { stockMap.clear(); }
}
