package com.trading.shareddto.shareddto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrendSignalEvent {
    private String symbol;
    private String trendType;  // e.g., "UPTREND", "DOWNTREND"
    private double confidenceScore;
    private LocalDateTime timestamp;
    
}

