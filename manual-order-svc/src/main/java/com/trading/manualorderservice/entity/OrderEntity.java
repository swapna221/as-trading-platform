package com.trading.manualorderservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private String workflow;          // EQUITY_INTRADAY / OPTION

    private String symbol;            // SBIN (equity) or underlying
    private String tradingSymbol;     // e.g. SBIN-Dec2025-950-CE for options
    private String securityId;        // Dhan security ID

    private String exchangeSegment;   // NSE_EQ / NSE_FNO / NSE_IDX
    private String transactionType;   // BUY/SELL
    private Integer quantity;

    private String orderType;         // MARKET/LIMIT
    private String productType;       // INTRADAY / DELIVERY

    @Enumerated(EnumType.STRING)
    private OrderRole role;           // ENTRY / STOPLOSS / TARGET

    private Long parentOrderId;       // for SL/TARGET → links to ENTRY

    // Risk params (percentage)
    private Double stoplossPercent;
    private Double targetPercent;
    private Double trailingPercent;

    // Absolute prices
    private Double entryPrice;
    private Double slPrice;
    private Double targetPrice;
    private Double triggerPrice;

    // Broker info
    private String brokerOrderId;
    private String orderStatus;       // NEW/PENDING/FILLED/FAILED
    @Lob
    @Column(columnDefinition = "TEXT")
    private String remark;


    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
