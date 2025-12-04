package com.trading.shareddto.shareddto;

import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeDto {
    private Long id;
    private String securityId;
    private String exchangeSegment;
    private String transactionType;
    private Integer quantity;
    private String orderType;
    private String productType;
    private Double price;
    private Double triggerPrice;
    private Integer disclosedQuantity;
    private Boolean afterMarketOrder;
    private String validity;
    private String amoTime;
    private Double boProfitValue;
    private Double boStopLossValue;
    private String stockName;
    private String tradingSymbol;
    private String customSymbol;
    private String orderStatus;
    private String jobStatus;
    private Integer lotSize;
    private String placeOrderType;
    // User details
    private Long userId;
    private String userName;
    private String brokerId;
    private LocalDateTime createdAt;
}

