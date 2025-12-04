package com.trading.shareddto.shareddto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AstroSignalEvent {
    private String zodiacSign;
    private String prediction;
    private String signal; // e.g., BUY, SELL, HOLD
    private String timestamp;
    private double age;
}
