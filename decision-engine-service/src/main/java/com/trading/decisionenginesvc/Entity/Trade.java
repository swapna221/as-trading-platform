package com.trading.decisionenginesvc.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "trades")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long securityId;
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
    private Long userId;
    private String userName;
    private String brokerId;
    private LocalDateTime createdAt;
}

