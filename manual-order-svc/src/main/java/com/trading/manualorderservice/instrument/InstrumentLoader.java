package com.trading.manualorderservice.instrument;

import com.opencsv.CSVReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class InstrumentLoader {

    private final InstrumentCache cache;

    @Value("${instrument.csv-url}")
    private String csvUrl;

    @Value("${instrument.reload-cron}")
    private String cronInfo; // just to show config present

    @PostConstruct
    public void initialLoad() { loadCsv(); }

    @Scheduled(cron = "${instrument.reload-cron}")
    public void scheduledReload() { loadCsv(); }

    private void loadCsv() {
        int loaded = 0;
        try (CSVReader reader = new CSVReader(new InputStreamReader(new URL(csvUrl).openStream()))) {
            String[] headers = reader.readNext();
            if (headers == null) throw new RuntimeException("Empty CSV");
            cache.clear();

            String[] row;
            while ((row = reader.readNext()) != null) {
                if (row.length != headers.length) continue;
                Map<String, String> map = new HashMap<>();
                for (int i = 0; i < headers.length; i++) map.put(headers[i].trim(), row[i].trim());

                // Match NSE equities exactly (from your screenshot)
                String exch   = map.getOrDefault("SEM_EXM_EXCH_ID", "");
                String seg    = map.getOrDefault("SEM_SEGMENT", "");
                String type   = map.getOrDefault("SEM_EXCH_INSTRUMENT_TYPE", "");
                String series = map.getOrDefault("SEM_SERIES", "");
                String symbol = map.getOrDefault("SEM_TRADING_SYMBOL", "");
                String secId  = map.getOrDefault("SEM_SMST_SECURITY_ID", "");

                if (!"NSE".equalsIgnoreCase(exch)) continue;
                if (!"E".equalsIgnoreCase(seg)) continue;
                if (!"ES".equalsIgnoreCase(type)) continue;   // equity stock
                if (!"EQ".equalsIgnoreCase(series)) continue; // EQ series
                if (symbol.isEmpty() || secId.isEmpty()) continue;

                cache.putStock(symbol, secId);
                loaded++;
            }

            log.info("✅ Instrument cache loaded: {} NSE EQ stocks (securityId pre-warmed).", loaded);
        } catch (Exception e) {
            log.error("❌ Failed loading instrument CSV: {}", e.getMessage());
        }
    }
}
