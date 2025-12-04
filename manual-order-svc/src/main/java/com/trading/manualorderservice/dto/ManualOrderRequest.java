package com.trading.manualorderservice.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ManualOrderRequest {
    private String underlying;
    private String optionType;
    private String moneyness;
    private String transactionType;
    private String tradeType;
    private String placeOrderType;
    private String dateInMonthYear;
    private int numberOfLots;
}

