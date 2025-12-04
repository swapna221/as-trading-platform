package com.trading.manualorderservice.stockfilter;

import com.opencsv.CSVReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class DhanStockHelper {

    private static final String CSV_URL = "https://images.dhan.co/api-data/api-scrip-master.csv";
    private final Map<String, StockRow> symbolMap = new ConcurrentHashMap<>();

    public DhanStockHelper() {
        try {
            log.info("📥 Loading Dhan master CSV for Stocks...");
            loadMasterCsv();
            log.info("✅ Loaded {} NSE/BSE stock entries.", symbolMap.size());
        } catch (Exception e) {
            log.error("❌ Failed to initialize DhanStockHelper: {}", e.getMessage());
        }
    }

    private void loadMasterCsv() throws Exception {
        try (CSVReader reader = new CSVReader(new InputStreamReader(new URL(CSV_URL).openStream()))) {
            String[] headers = reader.readNext();
            if (headers == null) throw new RuntimeException("Empty CSV");

            String[] row;
            int count = 0;
            while ((row = reader.readNext()) != null) {
                if (row.length != headers.length) continue;

                Map<String, String> map = new HashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    map.put(headers[i].trim(), row[i].trim());
                }

                String exch = map.getOrDefault("SEM_EXM_EXCH_ID", "");
                String segment = map.getOrDefault("SEM_SEGMENT", "");
                String type = map.getOrDefault("SEM_EXCH_INSTRUMENT_TYPE", "");
                String series = map.getOrDefault("SEM_SERIES", "");
                String symbol = map.getOrDefault("SEM_TRADING_SYMBOL", "");

                // Only equities
                if (!("NSE".equalsIgnoreCase(exch) || "BSE".equalsIgnoreCase(exch))) continue;
                if (!"E".equalsIgnoreCase(segment)) continue;
                if (!"ES".equalsIgnoreCase(type)) continue;
                if (!"EQ".equalsIgnoreCase(series)) continue;
                if (symbol == null || symbol.isEmpty()) continue;

                StockRow s = new StockRow(map);
                symbolMap.putIfAbsent(symbol.toUpperCase(), s);
                count++;
            }

            log.info("✅ Loaded {} NSE/BSE stock entries.", count);
        }
    }

    public Optional<String> getSecurityId(String symbol) {
        StockRow row = symbolMap.get(symbol.toUpperCase());
        return Optional.ofNullable(row != null ? row.securityId : null);
    }

    public Optional<String> getExchange(String symbol) {
        StockRow row = symbolMap.get(symbol.toUpperCase());
        return Optional.ofNullable(row != null ? row.exchangeId : null);
    }

    public Optional<Double> getLotSize(String symbol) {
        StockRow row = symbolMap.get(symbol.toUpperCase());
        return Optional.ofNullable(row != null ? row.lotUnits : 1.0);
    }

    /** ⭐ ADD THIS — CRITICAL FOR SL/TARGET ORDERS */
    public Optional<Double> getTickSize(String symbol) {
        StockRow row = symbolMap.get(symbol.toUpperCase());
        return Optional.ofNullable(row != null ? row.tickSize : null);
    }

    public boolean exists(String symbol) {
        return symbolMap.containsKey(symbol.toUpperCase());
    }
}
