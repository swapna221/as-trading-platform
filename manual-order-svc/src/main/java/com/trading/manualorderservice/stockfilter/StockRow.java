package com.trading.manualorderservice.stockfilter;

import java.util.Map;

public class StockRow {

    public final String securityId;
    public final String tradingSymbol;
    public final String exchangeId;
    public final String segment;
    public final String series;
    public final String instrumentType;
    public final double lotUnits;
    public final double tickSize;   // ⭐ REQUIRED FOR SL/TARGET ROUNDING

    public StockRow(Map<String, String> row) {
        this.securityId = row.getOrDefault("SEM_SMST_SECURITY_ID", "").trim();
        this.tradingSymbol = row.getOrDefault("SEM_TRADING_SYMBOL", "").trim();
        this.exchangeId = row.getOrDefault("SEM_EXM_EXCH_ID", "").trim();
        this.segment = row.getOrDefault("SEM_SEGMENT", "").trim();
        this.series = row.getOrDefault("SEM_SERIES", "").trim();
        this.instrumentType = row.getOrDefault("SEM_EXCH_INSTRUMENT_TYPE", "").trim();

        // LOT SIZE
        double lot;
        try {
            lot = Double.parseDouble(row.getOrDefault("SEM_LOT_UNITS", "1"));
        } catch (Exception e) {
            lot = 1;
        }
        this.lotUnits = lot;

        // ⭐ TICK SIZE
        double tick;
        try {
            tick = Double.parseDouble(row.getOrDefault("SEM_TICK_SIZE", "0.05"));
        } catch (Exception e) {
            tick = 0.05;
        }
        this.tickSize = tick;
    }

    @Override
    public String toString() {
        return exchangeId + ":" + tradingSymbol +
                " (ID=" + securityId +
                ", lot=" + lotUnits +
                ", tick=" + tickSize + ")";
    }
}
