package com.trading.manualorderservice.util;

public class SegmentMapper {

    /**
     * Converts internal segment → Dhan API expected segment.
     * Use for REST LTP API and placeOrder API.
     */
    public static String toDhanApi(String internal) {
        if (internal == null) return null;

        return switch (internal.toUpperCase()) {
            case "NSE_EQ"  -> "NSE_EQ";   // same as internal
            case "NSE_FNO" -> "NSE_FNO";  // same as internal
            case "NSE_IDX" -> "IDX_I";    // map internal → Dhan index segment
            case "IDX_I"   -> "IDX_I";    // already correct
            default        -> internal;   // fallback
        };
    }

    /**
     * Converts internal segment → Dhan WebSocket segment.
     */
    public static String toWebSocket(String internal) {
        if (internal == null) return null;

        return switch (internal.toUpperCase()) {
            case "NSE_EQ"  -> "NSE";
            case "NSE_FNO" -> "NFO";
            case "NSE_IDX" -> "IDX_I";  // Dhan WebSocket also uses IDX_I
            default        -> internal;
        };
    }

    /**
     * Converts Dhan API segment → internal normalized segment.
     */
    public static String toInternal(String dhanSegment) {
        if (dhanSegment == null) return null;

        return switch (dhanSegment.toUpperCase()) {
            case "NSE_EQ" -> "NSE_EQ";
            case "NSE_FNO" -> "NSE_FNO";
            case "IDX_I" -> "NSE_IDX"; // normalize
            default -> dhanSegment;
        };
    }
}
