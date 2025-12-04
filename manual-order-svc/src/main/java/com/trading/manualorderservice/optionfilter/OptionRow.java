package com.trading.manualorderservice.optionfilter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

public class OptionRow {

    public final String underlying;      // NIFTY, BANKNIFTY, SBIN...
    public final String tradingSymbol;   // NIFTY-Jan2026-23700-PE
    public final String customSymbol;    // NIFTY 27 JAN 23700 PUT
    public final LocalDate expiry;
    public final double strike;
    public final String optionType;      // CE / PE
    public final int lotSize;
    public final String securityId;      // SEM_SMST_SECURITY_ID
    public final String expiryFlag;      // M / W / etc.
    public final double tickSize;        // in rupees (0.05 etc.)
    public final String exchangeId;      // NSE
    public final String segment;         // D
    public final String instrumentName;  // OPTIDX / OPTSTK
    public final String instrumentType;  // OP

    public OptionRow(Map<String, String> row, String underlyingKey) {
        this.underlying     = underlyingKey;
        this.tradingSymbol  = row.getOrDefault("SEM_TRADING_SYMBOL", "").trim();
        this.customSymbol   = row.getOrDefault("SEM_CUSTOM_SYMBOL", "").trim();
        this.optionType     = row.getOrDefault("SEM_OPTION_TYPE", "").trim();
        this.securityId     = row.getOrDefault("SEM_SMST_SECURITY_ID", "").trim();
        this.expiryFlag     = row.getOrDefault("SEM_EXPIRY_FLAG", "").trim();
        this.exchangeId     = row.getOrDefault("SEM_EXM_EXCH_ID", "").trim();
        this.segment        = row.getOrDefault("SEM_SEGMENT", "").trim();
        this.instrumentName = row.getOrDefault("SEM_INSTRUMENT_NAME", "").trim();
        this.instrumentType = row.getOrDefault("SEM_EXCH_INSTRUMENT_TYPE", "").trim();

        // expiry date: "30-12-2025 14:30" or "2024-08-28 14:30"
        String rawDate = row.getOrDefault("SEM_EXPIRY_DATE", "").trim();
        if (!rawDate.isEmpty()) {
            String datePart = rawDate.split(" ")[0];
            this.expiry = parseDate(datePart);
        } else {
            this.expiry = null;
        }

        String strikeStr   = row.getOrDefault("SEM_STRIKE_PRICE", "0").trim();
        this.strike        = strikeStr.isEmpty() ? 0.0 : Double.parseDouble(strikeStr);

        String lotStr      = row.getOrDefault("SEM_LOT_UNITS", "1").trim();
        this.lotSize       = (int) Double.parseDouble(lotStr);

        String tickStr     = row.getOrDefault("SEM_TICK_SIZE", "0.05").trim();
        this.tickSize      = parseTick(tickStr);
    }

    private static LocalDate parseDate(String s) {
        // try yyyy-MM-dd
        try {
            DateTimeFormatter f1 = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            return LocalDate.parse(s, f1);
        } catch (DateTimeParseException ignored) {}

        // try dd-MM-yyyy
        try {
            DateTimeFormatter f2 = DateTimeFormatter.ofPattern("dd-MM-yyyy");
            return LocalDate.parse(s, f2);
        } catch (DateTimeParseException ignored) {}

        return null;
    }

    // Dhan CSV uses tick "5" for 5 paise → 0.05 rupees
    private static double parseTick(String s) {
        double raw = Double.parseDouble(s);
        if (raw > 1.0) {
            return raw / 100.0;
        }
        return raw;
    }
}
