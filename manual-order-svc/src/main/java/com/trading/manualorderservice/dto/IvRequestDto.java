package com.trading.manualorderservice.dto;

import lombok.Data;

@Data
public class IvRequestDto {
    private String workflow;       // always OPTION
    private String underlying;
    private String optionType;     // CE / PE
    private String moneyness;      // ATM / ITM / OTM
    private String transactionType;
    private String tradeType;
    private String dateInMonthYear; // DEC-2025
}
