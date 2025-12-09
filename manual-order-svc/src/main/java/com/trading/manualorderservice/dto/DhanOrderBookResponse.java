package com.trading.manualorderservice.dto;

import lombok.Data;

@Data
public class DhanOrderBookResponse {
    private String orderId;
    private String dhanClientId;
    private String correlationId;
    private String orderStatus;
    private String transactionType;
    private String exchangeSegment;
    private String productType;
    private String orderType;
    private String validity;
    private String tradingSymbol;
    private String securityId;

    private Integer quantity;
    private Integer disclosedQuantity;
    private Double price;
    private Double triggerPrice;

    private Boolean afterMarketOrder;

    private Double boProfitValue;
    private Double boStopLossValue;

    private String legName;

    private String createTime;
    private String updateTime;
    private String exchangeTime;

    private String drvExpiryDate;
    private String drvOptionType;
    private Double drvStrikePrice;

    private String omsErrorCode;
    private String omsErrorDescription;

    private Integer remainingQuantity;
    private Integer filledQty;
    private Double averageTradedPrice;
}
