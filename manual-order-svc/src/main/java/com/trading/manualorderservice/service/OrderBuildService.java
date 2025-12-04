//package com.trading.manualorderservice.service;
//
//import com.trading.manualorderservice.controller.ManualOrderController;
//import com.trading.manualorderservice.dhan.DhanOrderClient;
//import com.trading.manualorderservice.entity.OrderEntity;
//import com.trading.manualorderservice.entity.OrderRole;
//import com.trading.manualorderservice.kafka.ManualTradeProducer;
//import com.trading.manualorderservice.optionfilter.DhanOptionHelper;
//import com.trading.manualorderservice.optionfilter.OptionOrderHelper;
//import com.trading.manualorderservice.repo.OrderRepository;
//import com.trading.manualorderservice.stockfilter.DhanStockHelper;
//import com.trading.shareddto.entity.BrokerUserDetails;
//import com.trading.shareddto.shareddto.TradeDto;
//import jakarta.annotation.PostConstruct;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//
//import java.util.Map;
//import java.util.Optional;
//
//@Service
//@RequiredArgsConstructor
//@Slf4j
//public class OrderBuildService {
//
//    private final ManualTradeProducer manualTradeProducer;
//    private final DhanAllApis dhanAllApis;
//    private final DhanOrderClient dhanOrderClient;
//    private final OrderRepository orderRepository;
//    private final DhanStockHelper dhanStockHelper;
//
//    private OptionOrderHelper optionHelper;
//
//    @PostConstruct
//    public void initOptionHelper() throws Exception {
//        var rows = DhanOptionHelper.loadMasterCsv();
//        this.optionHelper = new OptionOrderHelper(rows);
//    }
//
//    public TradeDto buildOrder(Object reqObj, BrokerUserDetails creds, Long userId) throws Exception {
//        ManualOrderController.Req req = (ManualOrderController.Req) reqObj;
//
//        String workflow = Optional.ofNullable(req.workflow()).orElse("").toUpperCase();
//
//        return switch (workflow) {
//            case "EQUITY_INTRADAY" -> buildEquityIntradayWithSlTarget(req, creds, userId);
//            case "OPTION"          -> buildOptionWithSlTarget(req, creds, userId);
//            default -> throw new IllegalArgumentException("Unsupported workflow: " + workflow);
//        };
//    }
//
//    // ========================================================================
//    //                      EQUITY INTRADAY + SL/TGT
//    // ========================================================================
//    private TradeDto buildEquityIntradayWithSlTarget(ManualOrderController.Req req,
//                                                     BrokerUserDetails creds,
//                                                     Long userId) throws Exception {
//
//        String symbol = req.symbol().toUpperCase();
//
//        // 1️⃣ Get security ID from stock master
//        String securityId = dhanStockHelper.getSecurityId(symbol)
//                .orElseThrow(() -> new IllegalArgumentException("Unknown symbol: " + symbol));
//
//        // 2️⃣ Fetch live LTP - entry price
//        double entryPrice = dhanAllApis.fetchLTPStock(Integer.parseInt(securityId), creds);
//
//        // 3️⃣ Extract SL/Target % (may be 0 if not provided)
//        double slPct       = Optional.ofNullable(req.stoplossPercent()).orElse(0.0);
//        double tgtPct      = Optional.ofNullable(req.targetPercent()).orElse(0.0);
//        double trailingPct = Optional.ofNullable(req.trailingPercent()).orElse(0.0);
//
//        // 4️⃣ Get tick size (from DhanStockHelper; fallback 0.05)
//        double tick = dhanStockHelper.getTickSize(symbol).orElse(0.05);
//
//        // 5️⃣ Calculate raw SL/Target
//        double rawSl  = calculateRawSl(entryPrice, slPct, req.transactionType());
//        double rawTgt = calculateRawTarget(entryPrice, tgtPct, req.transactionType());
//
//        // 6️⃣ Tick-size adjusted values
//        double slPrice     = roundToTick(rawSl, tick);
//        double targetPrice = roundToTick(rawTgt, tick);
//
//        log.info("[EQ] {} LTP={} SL raw={}→{} TGT raw={}→{} tick={}",
//                symbol, entryPrice, rawSl, slPrice, rawTgt, targetPrice, tick);
//
//        // 7️⃣ Save ENTRY (MARKET)
//        OrderEntity entry = OrderEntity.builder()
//                .userId(userId)
//                .workflow(req.workflow())
//                .symbol(symbol)
//                .tradingSymbol(symbol)
//                .securityId(securityId)
//                .exchangeSegment("NSE_EQ")
//                .transactionType(req.transactionType())
//                .quantity(req.quantity())
//                .orderType("MARKET")
//                .productType(req.tradeType())              // usually INTRADAY
//                .role(OrderRole.ENTRY)
//                .entryPrice(entryPrice)
//                .stoplossPercent(slPct)
//                .targetPercent(tgtPct)
//                .trailingPercent(trailingPct)
//                .orderStatus("NEW")
//                .build();
//        entry = orderRepository.save(entry);
//
//        // 8️⃣ Save SL "template" row (reverse side)
//        OrderEntity sl = OrderEntity.builder()
//                .userId(userId)
//                .workflow(req.workflow())
//                .symbol(symbol)
//                .tradingSymbol(symbol)
//                .securityId(securityId)
//                .exchangeSegment("NSE_EQ")
//                .transactionType(reverseSide(req.transactionType()))
//                .quantity(req.quantity())
//                .orderType("STOP_LOSS")                    // internal label; broker uses same enum
//                .productType(req.tradeType())
//                .role(OrderRole.STOPLOSS)
//                .parentOrderId(entry.getId())
//                .slPrice(slPrice)
//                .orderStatus("NEW")
//                .build();
//        sl = orderRepository.save(sl);
//
//        // 9️⃣ Save TARGET row (LIMIT)
//        OrderEntity target = OrderEntity.builder()
//                .userId(userId)
//                .workflow(req.workflow())
//                .symbol(symbol)
//                .tradingSymbol(symbol)
//                .securityId(securityId)
//                .exchangeSegment("NSE_EQ")
//                .transactionType(reverseSide(req.transactionType()))
//                .quantity(req.quantity())
//                .orderType("LIMIT")
//                .productType(req.tradeType())
//                .role(OrderRole.TARGET)
//                .parentOrderId(entry.getId())
//                .targetPrice(targetPrice)
//                .orderStatus("NEW")
//                .build();
//        target = orderRepository.save(target);
//
//        // 🔟 Place ENTRY (MARKET) with Dhan
//        var entryResult = dhanOrderClient.placeOrder(
//                creds,
//                "NSE_EQ",
//                entry.getTransactionType(),
//                entry.getProductType(),
//                "MARKET",
//                securityId,
//                entry.getQuantity(),
//                0.0,
//                0.0
//        );
//
//        if (!entryResult.isOk()) {
//            entry.setOrderStatus("FAILED");
//            entry.setRemark("Entry failed: " + entryResult.getRaw());
//            orderRepository.save(entry);
//
//            sl.setOrderStatus("CANCELLED");
//            target.setOrderStatus("CANCELLED");
//            orderRepository.save(sl);
//            orderRepository.save(target);
//
//            return minimalDto(req, entryPrice, userId, securityId);
//        }
//
//        entry.setBrokerOrderId(entryResult.getOrderId());
//        entry.setOrderStatus(entryResult.getStatus());
//        entry.setRemark("ENTRY sent to Dhan");
//        orderRepository.save(entry);
//
//        // 1️⃣1️⃣ Wait until ENTRY is FILLED
//        boolean filled = waitUntilOrderFilled(creds, entry.getBrokerOrderId());
//        if (!filled) {
//            entry.setOrderStatus("FAILED");
//            entry.setRemark("Entry not filled → SL/TGT cancelled");
//            orderRepository.save(entry);
//
//            sl.setOrderStatus("CANCELLED");
//            target.setOrderStatus("CANCELLED");
//            orderRepository.save(sl);
//            orderRepository.save(target);
//
//            return minimalDto(req, entryPrice, userId, securityId);
//        }
//
//        // ⭐ Mark ENTRY as FILLED (important for Trailing/OCO engines)
//        entry.setOrderStatus("FILLED");
//        orderRepository.save(entry);
//
//        // 1️⃣2️⃣ Compute trigger price for SL (Dhan rule: trigger > price for SELL, trigger < price for BUY)
//        double triggerPrice;
//        if ("BUY".equalsIgnoreCase(req.transactionType())) {
//            // BUY entry → SL is SELL below entry → trigger must be ABOVE limit
//            triggerPrice = slPrice + tick;
//        } else {
//            // SELL entry → SL is BUY above entry → trigger must be BELOW limit
//            triggerPrice = slPrice - tick;
//        }
//        triggerPrice = roundToTick(triggerPrice, tick);
//        sl.setTriggerPrice(triggerPrice);
//        orderRepository.save(sl);
//
//        // 1️⃣3️⃣ Place SL on Dhan
//        var slResult = dhanOrderClient.placeOrder(
//                creds,
//                sl.getExchangeSegment(),
//                sl.getTransactionType(),
//                sl.getProductType(),
//                "STOP_LOSS",
//                securityId,
//                sl.getQuantity(),
//                slPrice,        // limit
//                triggerPrice    // trigger
//        );
//
//        if (slResult.isOk()) {
//            sl.setOrderStatus(slResult.getStatus());
//            sl.setBrokerOrderId(slResult.getOrderId());
//            sl.setRemark("SL placed");
//        } else {
//            sl.setOrderStatus("FAILED");
//            sl.setRemark("SL failed: " + slResult.getRaw());
//        }
//        orderRepository.save(sl);
//
//        // 1️⃣4️⃣ Place TARGET (LIMIT)
//        var tgtResult = dhanOrderClient.placeOrder(
//                creds,
//                target.getExchangeSegment(),
//                target.getTransactionType(),
//                target.getProductType(),
//                "LIMIT",
//                securityId,
//                target.getQuantity(),
//                targetPrice,
//                0.0
//        );
//
//        if (tgtResult.isOk()) {
//            target.setOrderStatus(tgtResult.getStatus());
//            target.setBrokerOrderId(tgtResult.getOrderId());
//            target.setRemark("Target placed");
//        } else {
//            target.setOrderStatus("FAILED");
//            target.setRemark("Target failed: " + tgtResult.getRaw());
//        }
//        orderRepository.save(target);
//
//        // 1️⃣5️⃣ Publish final TradeDto
//        TradeDto dto = minimalDto(req, entryPrice, userId, securityId);
//        manualTradeProducer.publishToKafka(dto);
//
//        return dto;
//    }
//
//    // ========================================================================
//    //                      OPTION (STOCK + INDEX) + SL/TGT
//    // ========================================================================
//    // ============= OPTION WORKFLOW =============
//
//    private TradeDto buildOptionWithSlTarget(ManualOrderController.Req req,
//                                             BrokerUserDetails creds,
//                                             Long userId) throws Exception {
//
//        String underlying = req.underlying().toUpperCase();
//
//        if (!optionHelper.isValidUnderlying(underlying)) {
//            throw new IllegalArgumentException("Invalid underlying: " + underlying);
//        }
//
//        // 1️⃣ Get underlying SPOT / INDEX securityId & spot price (for moneyness)
//        Optional<String> underlyingSecIdOpt = optionHelper.resolveUnderlyingSpotSecurityId(underlying);
//
//
//        int underlyingSecId = Integer.parseInt(
//                underlyingSecIdOpt.orElseThrow(
//                        () -> new IllegalArgumentException("No spot securityId for underlying: " + underlying)
//                )
//        );
//
//        double spotPrice = dhanAllApis.fetchLTPStock(underlyingSecId, creds);
//
//        // 2️⃣ Build OPTION contract using helper (monthly expiry only)
//        Map<String, Object> tradeData = optionHelper.buildOrder(
//                underlying,                   // use canonical underlying
//                req.optionType(),
//                req.moneyness(),
//                req.transactionType(),
//                req.tradeType(),
//                req.placeOrderType(),
//                req.numberOfLots(),
//                spotPrice,
//                req.dateInMonthYear()
//        );
//
//        String securityId      = (String) tradeData.get("security_id");
//        String exchangeSegment = (String) tradeData.get("exchange_segment");
//        String tradingSymbol   = (String) tradeData.get("trading_symbol");
//        int quantity           = (int) tradeData.get("quantity");
//        double tick            = (double) tradeData.get("tick_size");
//
//        // 3️⃣ LTP of OPTION entry
//        double entryPrice = dhanAllApis.fetchLTPStock(Integer.parseInt(securityId), creds);
//
//        double slPct  = Optional.ofNullable(req.stoplossPercent()).orElse(0.0);
//        double tgtPct = Optional.ofNullable(req.targetPercent()).orElse(0.0);
//        double trailingPct = Optional.ofNullable(req.trailingPercent()).orElse(0.0);
//
//        double slRaw   = calculateRawSl(entryPrice, slPct, req.transactionType());
//        double tgtRaw  = calculateRawTarget(entryPrice, tgtPct, req.transactionType());
//
//        double slPrice = roundToTick(slRaw, tick);
//        double targetPrice = roundToTick(tgtRaw, tick);
//
//        double triggerPrice = req.transactionType().equalsIgnoreCase("BUY")
//                ? slPrice + tick : slPrice - tick;
//
//        triggerPrice = roundToTick(triggerPrice, tick);
//
//        // Save ENTRY
//        OrderEntity entry = OrderEntity.builder()
//                .userId(userId)
//                .workflow(req.workflow())
//                .symbol(underlying)
//                .tradingSymbol(tradingSymbol)
//                .securityId(securityId)
//                .exchangeSegment(exchangeSegment)
//                .transactionType(req.transactionType())
//                .quantity(quantity)
//                .orderType("MARKET")
//                .productType(req.tradeType())
//                .role(OrderRole.ENTRY)
//                .entryPrice(entryPrice)
//                .stoplossPercent(slPct)
//                .targetPercent(tgtPct)
//                .trailingPercent(trailingPct)
//                .orderStatus("NEW")
//                .build();
//
//        entry = orderRepository.save(entry);
//
//        // Save SL
//        OrderEntity sl = OrderEntity.builder()
//                .userId(userId)
//                .workflow(req.workflow())
//                .symbol(underlying)
//                .tradingSymbol(tradingSymbol)
//                .securityId(securityId)
//                .exchangeSegment(exchangeSegment)
//                .transactionType(reverseSide(req.transactionType()))
//                .quantity(quantity)
//                .orderType("STOP_LOSS")
//                .productType(req.tradeType())
//                .role(OrderRole.STOPLOSS)
//                .parentOrderId(entry.getId())
//                .slPrice(slPrice)
//                .triggerPrice(triggerPrice)
//                .orderStatus("NEW")
//                .build();
//
//        sl = orderRepository.save(sl);
//
//        // Save TARGET
//        OrderEntity target = OrderEntity.builder()
//                .userId(userId)
//                .workflow(req.workflow())
//                .symbol(underlying)
//                .tradingSymbol(tradingSymbol)
//                .securityId(securityId)
//                .exchangeSegment(exchangeSegment)
//                .transactionType(reverseSide(req.transactionType()))
//                .quantity(quantity)
//                .orderType("LIMIT")
//                .productType(req.tradeType())
//                .role(OrderRole.TARGET)
//                .parentOrderId(entry.getId())
//                .targetPrice(targetPrice)
//                .orderStatus("NEW")
//                .build();
//
//        target = orderRepository.save(target);
//
//        // Place ENTRY
//        var entryRes = dhanOrderClient.placeOrder(
//                creds, exchangeSegment, req.transactionType(),
//                req.tradeType(), "MARKET",
//                securityId, quantity,
//                0, 0
//        );
//
//        if (!entryRes.isOk()) {
//            entry.setOrderStatus("FAILED");
//            orderRepository.save(entry);
//            sl.setOrderStatus("CANCELLED");
//            target.setOrderStatus("CANCELLED");
//            orderRepository.save(sl);
//            orderRepository.save(target);
//            return mapToTradeDto(tradeData, userId);
//        }
//
//        entry.setOrderStatus(entryRes.getStatus());
//        entry.setBrokerOrderId(entryRes.getOrderId());
//        orderRepository.save(entry);
//
//        // Wait for ENTRY to fill
//        boolean filled = waitUntilOrderFilled(creds, entry.getBrokerOrderId());
//
//        if (!filled) {
//            entry.setOrderStatus("FAILED");
//            orderRepository.save(entry);
//            sl.setOrderStatus("CANCELLED");
//            target.setOrderStatus("CANCELLED");
//            orderRepository.save(sl);
//            orderRepository.save(target);
//            return mapToTradeDto(tradeData, userId);
//        }
//
//        // Place SL
//        var slRes = dhanOrderClient.placeOrder(
//                creds, exchangeSegment,
//                sl.getTransactionType(),
//                sl.getProductType(),
//                "STOP_LOSS",
//                securityId, quantity,
//                slPrice, triggerPrice
//        );
//
//        if (slRes.isOk()) {
//            sl.setOrderStatus(slRes.getStatus());
//            sl.setBrokerOrderId(slRes.getOrderId());
//        } else {
//            sl.setOrderStatus("FAILED");
//        }
//        orderRepository.save(sl);
//
//        // Place TARGET
//        var tgtRes = dhanOrderClient.placeOrder(
//                creds, exchangeSegment,
//                target.getTransactionType(),
//                target.getProductType(),
//                "LIMIT",
//                securityId, quantity,
//                targetPrice, 0
//        );
//
//        if (tgtRes.isOk()) {
//            target.setOrderStatus(tgtRes.getStatus());
//            target.setBrokerOrderId(tgtRes.getOrderId());
//        } else {
//            target.setOrderStatus("FAILED");
//        }
//        orderRepository.save(target);
//
//        tradeData.put("price", entryPrice);
//        TradeDto dto = mapToTradeDto(tradeData, userId);
//
//        manualTradeProducer.publishToKafka(dto);
//        return dto;
//    }
//
//
//    // ========================================================================
//    //                               HELPERS
//    // ========================================================================
//
//    private boolean waitUntilOrderFilled(BrokerUserDetails creds, String orderId) {
//
//        int maxChecks = 40;   // ~40 sec
//        int delay = 1000;     // 1 sec
//
//        for (int i = 0; i < maxChecks; i++) {
//
//            var status = dhanOrderClient.getOrderStatus(creds, orderId);
//
//            if (!status.isOk()) continue;
//
//            String s = status.getStatus().toUpperCase();
//
//            log.info("⏳ ENTRY STATUS CHECK {} => {}", orderId, s);
//
//            if (s.contains("TRADED") || s.contains("FILLED") || s.contains("COMPLETED")) {
//                return true;
//            }
//
//            if (s.contains("REJECTED") || s.contains("CANCELLED")) {
//                return false;
//            }
//
//            try {
//                Thread.sleep(delay);
//            } catch (InterruptedException ignored) {}
//        }
//
//        return false;  // timed out
//    }
//
//    private double calculateRawSl(double entryPrice, double slPct, String side) {
//        if (slPct <= 0) return entryPrice; // no SL move
//        return side.equalsIgnoreCase("BUY")
//                ? entryPrice * (1 - slPct / 100.0)
//                : entryPrice * (1 + slPct / 100.0);
//    }
//
//    private double calculateRawTarget(double entryPrice, double tgtPct, String side) {
//        if (tgtPct <= 0) return entryPrice; // no target move
//        return side.equalsIgnoreCase("BUY")
//                ? entryPrice * (1 + tgtPct / 100.0)
//                : entryPrice * (1 - tgtPct / 100.0);
//    }
//
//    private double roundToTick(double price, double tick) {
//        if (tick <= 0) return price;
//        return Math.round(price / tick) * tick;
//    }
//
//    private String reverseSide(String side) {
//        return side.equalsIgnoreCase("BUY") ? "SELL" : "BUY";
//    }
//
//    // reuse your existing mapToTradeDto for OPTION
//    private TradeDto mapToTradeDto(Map<String, Object> tradeData, Long userId) {
//        return TradeDto.builder()
//                .securityId((String) tradeData.get("security_id"))
//                .exchangeSegment((String) tradeData.get("exchange_segment"))
//                .transactionType((String) tradeData.get("transaction_type"))
//                .quantity((Integer) tradeData.get("quantity"))
//                .orderType((String) tradeData.get("order_type"))
//                .productType((String) tradeData.get("product_type"))
//                .price(((Number) tradeData.get("price")).doubleValue())
//                .triggerPrice(((Number) tradeData.get("trigger_price")).doubleValue())
//                .disclosedQuantity((Integer) tradeData.get("disclosed_quantity"))
//                .afterMarketOrder((Boolean) tradeData.get("after_market_order"))
//                .validity((String) tradeData.get("validity"))
//                .amoTime((String) tradeData.get("amo_time"))
//                .boProfitValue((Double) tradeData.get("bo_profit_value"))
//                .boStopLossValue((Double) tradeData.get("bo_stop_loss_value"))
//                .stockName((String) tradeData.get("stock_name"))
//                .tradingSymbol((String) tradeData.get("trading_symbol"))
//                .customSymbol((String) tradeData.get("custom_Symbol"))
//                .orderStatus((String) tradeData.get("order_status"))
//                .jobStatus((String) tradeData.get("job_status"))
//                .lotSize((Integer) tradeData.get("lot_size"))
//                .placeOrderType((String) tradeData.get("place_order_type"))
//                .userId(userId)
//                .build();
//    }
//
//    private TradeDto minimalDto(ManualOrderController.Req req,
//                                double price,
//                                Long userId,
//                                String securityId) {
//        return TradeDto.builder()
//                .securityId(securityId)
//                .exchangeSegment("NSE_EQ")
//                .transactionType(req.transactionType())
//                .quantity(req.quantity())
//                .orderType(req.placeOrderType())
//                .productType(req.tradeType())
//                .price(price)
//                .triggerPrice(0.0)
//                .afterMarketOrder(false)
//                .validity("DAY")
//                .amoTime("OPEN")
//                .stockName(req.symbol() != null ? req.symbol() : req.underlying())
//                .tradingSymbol(req.symbol() != null ? req.symbol() : req.underlying())
//                .orderStatus("NEW")
//                .jobStatus("NEW")
//                .userId(userId)
//                .build();
//    }
//}



package com.trading.manualorderservice.service;

import com.trading.manualorderservice.controller.ManualOrderController;
import com.trading.manualorderservice.dhan.DhanOrderClient;
import com.trading.manualorderservice.entity.OrderEntity;
import com.trading.manualorderservice.entity.OrderRole;
import com.trading.manualorderservice.kafka.ManualTradeProducer;
import com.trading.manualorderservice.marketfeed.IndexPollingService;
import com.trading.manualorderservice.optionfilter.DhanOptionHelper;
import com.trading.manualorderservice.optionfilter.OptionOrderHelper;
import com.trading.manualorderservice.repo.OrderRepository;
import com.trading.manualorderservice.stockfilter.DhanStockHelper;
import com.trading.shareddto.entity.BrokerUserDetails;
import com.trading.shareddto.shareddto.TradeDto;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderBuildService {

    private final ManualTradeProducer manualTradeProducer;
    private final DhanAllApis dhanAllApis;
    private final DhanOrderClient dhanOrderClient;
    private final OrderRepository orderRepository;
    private final DhanStockHelper dhanStockHelper;
    private final IndexPollingService indexPollingService;

    @Autowired
    private OptionOrderHelper optionHelper;


    public TradeDto buildOrder(Object reqObj, BrokerUserDetails creds, Long userId) throws Exception {
        ManualOrderController.Req req = (ManualOrderController.Req) reqObj;

        String workflow = Optional.ofNullable(req.workflow()).orElse("").toUpperCase();

        return switch (workflow) {
            case "EQUITY_INTRADAY" -> buildEquityIntradayWithSlTarget(req, creds, userId);
            case "OPTION"          -> buildOptionWithSlTarget(req, creds, userId);
            default -> throw new IllegalArgumentException("Unsupported workflow: " + workflow);
        };
    }

    // ============================================================
    //                 EQUITY INTRADAY  (WORKING)
    // ============================================================
    private TradeDto buildEquityIntradayWithSlTarget(ManualOrderController.Req req,
                                                     BrokerUserDetails creds,
                                                     Long userId) throws Exception {

        String symbol = req.symbol();

        // 1️⃣ Get security ID (this is what already works in your flow)
        String securityId = dhanStockHelper.getSecurityId(symbol)
                .orElseThrow(() -> new IllegalArgumentException("Unknown symbol: " + symbol));

        // 2️⃣ Fetch live LTP - entry price
        double entryPrice = dhanAllApis.fetchLTPStock(Integer.parseInt(securityId), creds);

        // 3️⃣ Extract SL/Target %
        double slPct       = Optional.ofNullable(req.stoplossPercent()).orElse(0.0);
        double tgtPct      = Optional.ofNullable(req.targetPercent()).orElse(0.0);
        double trailingPct = Optional.ofNullable(req.trailingPercent()).orElse(0.0);

        // 4️⃣ Get tick size (NSE EQ = 0.05 for most)
        double tick = dhanStockHelper.getTickSize(symbol).orElse(0.05);

        // 5️⃣ Calculate raw SL/Target
        double rawSl  = calculateRawSl(entryPrice, slPct, req.transactionType());
        double rawTgt = calculateRawTarget(entryPrice, tgtPct, req.transactionType());

        // 6️⃣ Tick-size adjusted values
        double slPrice     = roundToTick(rawSl, tick);
        double targetPrice = roundToTick(rawTgt, tick);

        log.info("[EQ] {} LTP={} SL raw={}→{} TGT raw={}→{} tick={}",
                symbol, entryPrice, rawSl, slPrice, rawTgt, targetPrice, tick);

        // 7️⃣ Save ENTRY (MARKET)
        OrderEntity entry = OrderEntity.builder()
                .userId(userId)
                .workflow(req.workflow())
                .symbol(symbol)
                .tradingSymbol(symbol)
                .securityId(securityId)
                .exchangeSegment("NSE_EQ")
                .transactionType(req.transactionType())
                .quantity(req.quantity())
                .orderType("MARKET")
                .productType("INTRADAY")
                .role(OrderRole.ENTRY)
                .entryPrice(entryPrice)
                .stoplossPercent(slPct)
                .targetPercent(tgtPct)
                .trailingPercent(trailingPct)
                .orderStatus("NEW")
                .build();
        entry = orderRepository.save(entry);

        // 8️⃣ SL (reverse side)
        OrderEntity sl = OrderEntity.builder()
                .userId(userId)
                .workflow(req.workflow())
                .symbol(symbol)
                .tradingSymbol(symbol)
                .securityId(securityId)
                .exchangeSegment("NSE_EQ")
                .transactionType(reverseSide(req.transactionType()))
                .quantity(req.quantity())
                .orderType("SL") // internal label in DB
                .productType("INTRADAY")
                .role(OrderRole.STOPLOSS)
                .parentOrderId(entry.getId())
                .slPrice(slPrice)
                .orderStatus("NEW")
                .build();
        sl = orderRepository.save(sl);

        // 9️⃣ TARGET (LIMIT)
        OrderEntity target = OrderEntity.builder()
                .userId(userId)
                .workflow(req.workflow())
                .symbol(symbol)
                .tradingSymbol(symbol)
                .securityId(securityId)
                .exchangeSegment("NSE_EQ")
                .transactionType(reverseSide(req.transactionType()))
                .quantity(req.quantity())
                .orderType("LIMIT")
                .productType("INTRADAY")
                .role(OrderRole.TARGET)
                .parentOrderId(entry.getId())
                .targetPrice(targetPrice)
                .orderStatus("NEW")
                .build();
        target = orderRepository.save(target);

        // 🔟 Place ENTRY (MARKET) – SAME as your working logic
        var entryResult = dhanOrderClient.placeOrder(
                creds,
                "NSE_EQ",
                entry.getTransactionType(),
                "INTRADAY",
                "MARKET",
                securityId,
                req.quantity(),
                0.0,
                0.0
        );

        if (!entryResult.isOk()) {
            entry.setOrderStatus("FAILED");
            entry.setRemark("Entry failed: " + entryResult.getRaw());
            orderRepository.save(entry);

            sl.setOrderStatus("CANCELLED");
            target.setOrderStatus("CANCELLED");
            orderRepository.save(sl);
            orderRepository.save(target);

            return minimalDto(req, entryPrice, userId, securityId);
        }

        entry.setBrokerOrderId(entryResult.getOrderId());
        entry.setOrderStatus(entryResult.getStatus());
        entry.setRemark("ENTRY sent to Dhan");
        orderRepository.save(entry);

        // 1️⃣1️⃣ Wait until ENTRY is FILLED
        boolean filled = waitUntilOrderFilled(creds, entry.getBrokerOrderId());
        if (!filled) {
            entry.setOrderStatus("FAILED");
            entry.setRemark("Entry not filled → SL/TGT cancelled");
            orderRepository.save(entry);

            sl.setOrderStatus("CANCELLED");
            target.setOrderStatus("CANCELLED");
            orderRepository.save(sl);
            orderRepository.save(target);

            return minimalDto(req, entryPrice, userId, securityId);
        }

        // ⭐ Mark entry as FILLED
        entry.setOrderStatus("FILLED");
        orderRepository.save(entry);

        // 1️⃣2️⃣ Compute trigger price according to DHAN rules
        double triggerPrice;
        if ("BUY".equalsIgnoreCase(req.transactionType())) {
            // BUY → exit is SELL → trigger > limit
            triggerPrice = slPrice + tick;
        } else {
            // SELL → exit is BUY → trigger < limit
            triggerPrice = slPrice - tick;
        }

        // 1️⃣3️⃣ Place SL (STOP_LOSS = limit + trigger)
        var slResult = dhanOrderClient.placeOrder(
                creds,
                sl.getExchangeSegment(),
                sl.getTransactionType(),
                sl.getProductType(),
                "STOP_LOSS",
                securityId,
                sl.getQuantity(),
                slPrice,       // limit
                triggerPrice   // trigger
        );

        if (slResult.isOk()) {
            sl.setOrderStatus(slResult.getStatus());
            sl.setBrokerOrderId(slResult.getOrderId());
            sl.setRemark("SL placed");
        } else {
            sl.setOrderStatus("FAILED");
            sl.setRemark("SL failed: " + slResult.getRaw());
        }
        orderRepository.save(sl);

        // 1️⃣4️⃣ Place TARGET (LIMIT)
        var tgtResult = dhanOrderClient.placeOrder(
                creds,
                "NSE_EQ",
                target.getTransactionType(),
                "INTRADAY",
                "LIMIT",
                securityId,
                req.quantity(),
                targetPrice,
                0.0
        );

        if (tgtResult.isOk()) {
            target.setOrderStatus(tgtResult.getStatus());
            target.setBrokerOrderId(tgtResult.getOrderId());
            target.setRemark("Target placed");
        } else {
            target.setOrderStatus("FAILED");
            target.setRemark("Target failed: " + tgtResult.getRaw());
        }
        orderRepository.save(target);

        // 1️⃣5️⃣ Publish final event
        TradeDto dto = minimalDto(req, entryPrice, userId, securityId);
        manualTradeProducer.publishToKafka(dto);

        return dto;
    }

    // ============================================================
    //                    OPTION WORKFLOW
    //   (Stock options like SBIN – supported)
    //   (Index options NIFTY/BANKNIFTY – currently blocked with
    //    a clear error instead of weird JSON["2"] errors)
    // ============================================================
    private TradeDto buildOptionWithSlTarget(ManualOrderController.Req req,
                                             BrokerUserDetails creds,
                                             Long userId) throws Exception {

        String underlying = req.underlying().toUpperCase();

        // 1️⃣ Get spot LTP of UNDERLYING using existing, working equity infra
        //    - For stock underlyings (SBIN, TCS, etc) this uses NSE_EQ securityId
        //    - For NIFTY/BANKNIFTY this WILL currently throw a clear error
        //double spotPrice = resolveUnderlyingSpotLtp(underlying, creds, userId);
        double spotPrice = optionHelper.resolveSpotPrice(
                underlying,
                indexPollingService,
                dhanAllApis,
                creds,
                userId
        );


        // 2️⃣ Build OPTION contract (securityId, tradingSymbol, qty, tickSize, etc.)
        Map<String, Object> tradeData = optionHelper.buildOrder(
                underlying,
                req.optionType(),
                req.moneyness(),
                req.transactionType(),
                req.tradeType(),
                req.placeOrderType(),
                req.numberOfLots(),
                spotPrice,
                req.dateInMonthYear()
        );

        String securityId      = (String) tradeData.get("security_id");
        String exchangeSegment = (String) tradeData.get("exchange_segment");
        String tradingSymbol   = (String) tradeData.get("trading_symbol");
        int quantity           = (Integer) tradeData.get("quantity");
        double tick            = (Double) tradeData.getOrDefault("tick_size", 0.05);

        // 3️⃣ Fetch option LTP – ENTRY price
        //double entryPrice = dhanAllApis.fetchLTPStock(Integer.parseInt(securityId), creds);
        double entryPrice = dhanAllApis.fetchLtpOption(securityId, creds,userId);


        double slPct       = Optional.ofNullable(req.stoplossPercent()).orElse(0.0);
        double tgtPct      = Optional.ofNullable(req.targetPercent()).orElse(0.0);
        double trailingPct = Optional.ofNullable(req.trailingPercent()).orElse(0.0);

        // 4️⃣ Compute raw SL/TGT and tick-round them
        double rawSl  = calculateRawSl(entryPrice, slPct, req.transactionType());
        double rawTgt = calculateRawTarget(entryPrice, tgtPct, req.transactionType());

        double slPrice     = roundToTick(rawSl, tick);
        double targetPrice = roundToTick(rawTgt, tick);

        // Trigger must respect DHAN rule:
        //  - For SL SELL: trigger > price
        //  - For SL BUY: trigger < price
        double triggerPrice;
        if ("BUY".equalsIgnoreCase(req.transactionType())) {
            triggerPrice = slPrice + tick;
        } else {
            triggerPrice = slPrice - tick;
        }
        triggerPrice = roundToTick(triggerPrice, tick);

        log.info("[OPT] {} LTP={} SL raw={} -> {} (trigger={}) TGT raw={} -> {} tick={}",
                tradingSymbol, entryPrice, rawSl, slPrice, triggerPrice, rawTgt, targetPrice, tick);

        // 5️⃣ Persist ENTRY order
        OrderEntity entry = OrderEntity.builder()
                .userId(userId)
                .workflow(req.workflow() != null ? req.workflow() : "OPTION")
                .symbol(underlying)
                .tradingSymbol(tradingSymbol)
                .securityId(securityId)
                .exchangeSegment(exchangeSegment)
                .transactionType(req.transactionType())
                .quantity(quantity)
                .orderType("MARKET")
                .productType(req.tradeType())
                .role(OrderRole.ENTRY)
                .entryPrice(entryPrice)
                .stoplossPercent(slPct)
                .targetPercent(tgtPct)
                .trailingPercent(trailingPct)
                .orderStatus("NEW")
                .build();
        entry = orderRepository.save(entry);

        // 6️⃣ Persist SL order
        OrderEntity sl = OrderEntity.builder()
                .userId(userId)
                .workflow(entry.getWorkflow())
                .symbol(underlying)
                .tradingSymbol(tradingSymbol)
                .securityId(securityId)
                .exchangeSegment(exchangeSegment)
                .transactionType(reverseSide(req.transactionType()))
                .quantity(quantity)
                .orderType("STOP_LOSS")
                .productType(req.tradeType())
                .role(OrderRole.STOPLOSS)
                .parentOrderId(entry.getId())
                .slPrice(slPrice)
                .triggerPrice(triggerPrice)
                .orderStatus("NEW")
                .build();
        sl = orderRepository.save(sl);

        // 7️⃣ Persist TARGET order
        OrderEntity target = OrderEntity.builder()
                .userId(userId)
                .workflow(entry.getWorkflow())
                .symbol(underlying)
                .tradingSymbol(tradingSymbol)
                .securityId(securityId)
                .exchangeSegment(exchangeSegment)
                .transactionType(reverseSide(req.transactionType()))
                .quantity(quantity)
                .orderType("LIMIT")
                .productType(req.tradeType())
                .role(OrderRole.TARGET)
                .parentOrderId(entry.getId())
                .targetPrice(targetPrice)
                .orderStatus("NEW")
                .build();
        target = orderRepository.save(target);

        // 8️⃣ Place ENTRY – MARKET
        var entryResult = dhanOrderClient.placeOrder(
                creds,
                exchangeSegment,
                req.transactionType(),
                req.tradeType(),
                "MARKET",
                securityId,
                quantity,
                0.0,
                0.0
        );

        if (!entryResult.isOk()) {
            entry.setOrderStatus("FAILED");
            entry.setRemark("Entry failed: " + entryResult.getRaw());
            orderRepository.save(entry);

            sl.setOrderStatus("CANCELLED");
            target.setOrderStatus("CANCELLED");
            orderRepository.save(sl);
            orderRepository.save(target);

            TradeDto dtoFailed = mapToTradeDto(tradeData, userId);
            manualTradeProducer.publishToKafka(dtoFailed);
            return dtoFailed;
        }

        entry.setBrokerOrderId(entryResult.getOrderId());
        entry.setOrderStatus(entryResult.getStatus());
        orderRepository.save(entry);

        // 9️⃣ Wait until ENTRY is filled before placing SL/TGT
        boolean filled = waitUntilOrderFilled(creds, entry.getBrokerOrderId());
        if (!filled) {
            entry.setOrderStatus("FAILED");
            entry.setRemark("Entry not filled, abort SL/Target");
            orderRepository.save(entry);

            sl.setOrderStatus("CANCELLED");
            target.setOrderStatus("CANCELLED");
            orderRepository.save(sl);
            orderRepository.save(target);

            TradeDto dtoFailed = mapToTradeDto(tradeData, userId);
            manualTradeProducer.publishToKafka(dtoFailed);
            return dtoFailed;
        }

        // 🔟 Place SL
        var slResult = dhanOrderClient.placeOrder(
                creds,
                exchangeSegment,
                sl.getTransactionType(),
                sl.getProductType(),
                "STOP_LOSS",
                securityId,
                quantity,
                slPrice,
                triggerPrice
        );
        if (slResult.isOk()) {
            sl.setOrderStatus(slResult.getStatus());
            sl.setBrokerOrderId(slResult.getOrderId());
            sl.setRemark("SL placed");
        } else {
            sl.setOrderStatus("FAILED");
            sl.setRemark("SL failed: " + slResult.getRaw());
        }
        orderRepository.save(sl);

        // 1️⃣1️⃣ Place TARGET – LIMIT
        var tgtResult = dhanOrderClient.placeOrder(
                creds,
                exchangeSegment,
                target.getTransactionType(),
                target.getProductType(),
                "LIMIT",
                securityId,
                quantity,
                targetPrice,
                0.0
        );
        if (tgtResult.isOk()) {
            target.setOrderStatus(tgtResult.getStatus());
            target.setBrokerOrderId(tgtResult.getOrderId());
            target.setRemark("Target placed");
        } else {
            target.setOrderStatus("FAILED");
            target.setRemark("Target failed: " + tgtResult.getRaw());
        }
        orderRepository.save(target);

        // 1️⃣2️⃣ Build TradeDto for downstream
        tradeData.put("price", entryPrice);
        TradeDto dto = mapToTradeDto(tradeData, userId);
        manualTradeProducer.publishToKafka(dto);
        return dto;
    }

    // ============================================================
    //                        HELPERS
    // ============================================================

    /**
     * For now:
     *  - If underlying is a stock (SBIN, TCS, etc) → use DhanStockHelper (NSE_EQ) → fetchLTPStock
     *  - If not found in DhanStockHelper (likely NIFTY / BANKNIFTY) → throw clear error
     *
     * This AVOIDS the "JSONObject[\"2\"] not found" and "JSONObject[\"500112\"] not found"
     * errors you were seeing earlier.
     */

    private double resolveUnderlyingSpotLtp(String underlying, BrokerUserDetails creds, Long userId) throws Exception {

        String u = underlying.toUpperCase();

        // 1) Try stock equity (works for SBIN, TCS, RELIANCE)
        Optional<String> eqSecId = dhanStockHelper.getSecurityId(u);
        if (eqSecId.isPresent()) {
            return dhanAllApis.fetchLtpEquity(eqSecId.get(), creds, userId);
        }

        // 2️⃣ Index → use polling cache
        if (u.equals("NIFTY")) {
            Double v = indexPollingService.getNiftySpot();
            if (v == null) throw new RuntimeException("NIFTY spot not available yet");
            return v;
        }

        if (u.equals("BANKNIFTY")) {
            Double v = indexPollingService.getBankNiftySpot();
            if (v == null) throw new RuntimeException("BANKNIFTY spot not available yet");
            return v;
        }

        throw new IllegalArgumentException("Unknown underlying: " + u);
    }




    private boolean waitUntilOrderFilled(BrokerUserDetails creds, String orderId) {

        int maxChecks = 40;   // ~40 sec
        int delay = 1000;     // 1 sec

        for (int i = 0; i < maxChecks; i++) {

            var status = dhanOrderClient.getOrderStatus(creds, orderId);

            if (!status.isOk()) continue;

            String s = status.getStatus().toUpperCase();

            log.info("⏳ ENTRY STATUS CHECK {} => {}", orderId, s);

            if (s.contains("TRADED") || s.contains("FILLED") || s.contains("COMPLETED")) {
                return true;
            }

            if (s.contains("REJECTED") || s.contains("CANCELLED")) {
                return false;
            }

            try { Thread.sleep(delay); } catch (InterruptedException ignored) {}
        }

        return false;  // timed out
    }

    private double calculateRawSl(double entryPrice, double slPct, String side) {
        return side.equalsIgnoreCase("BUY")
                ? entryPrice * (1 - slPct / 100.0)
                : entryPrice * (1 + slPct / 100.0);
    }

    private double calculateRawTarget(double entryPrice, double tgtPct, String side) {
        return side.equalsIgnoreCase("BUY")
                ? entryPrice * (1 + tgtPct / 100.0)
                : entryPrice * (1 - tgtPct / 100.0);
    }

    private double roundToTick(double price, double tick) {
        if (tick <= 0) return price;
        return Math.round(price / tick) * tick;
    }

    private String reverseSide(String side) {
        return side.equalsIgnoreCase("BUY") ? "SELL" : "BUY";
    }

    // reuse your existing mapToTradeDto for OPTION
    private TradeDto mapToTradeDto(Map<String, Object> tradeData, Long userId) {
        return TradeDto.builder()
                .securityId((String) tradeData.get("security_id"))
                .exchangeSegment((String) tradeData.get("exchange_segment"))
                .transactionType((String) tradeData.get("transaction_type"))
                .quantity((Integer) tradeData.get("quantity"))
                .orderType((String) tradeData.get("order_type"))
                .productType((String) tradeData.get("product_type"))
                .price(((Number) tradeData.get("price")).doubleValue())
                .triggerPrice(((Number) tradeData.get("trigger_price")).doubleValue())
                .disclosedQuantity((Integer) tradeData.get("disclosed_quantity"))
                .afterMarketOrder((Boolean) tradeData.get("after_market_order"))
                .validity((String) tradeData.get("validity"))
                .amoTime((String) tradeData.get("amo_time"))
                .boProfitValue((Double) tradeData.get("bo_profit_value"))
                .boStopLossValue((Double) tradeData.get("bo_stop_loss_value"))
                .stockName((String) tradeData.get("stock_name"))
                .tradingSymbol((String) tradeData.get("trading_symbol"))
                .customSymbol((String) tradeData.get("custom_Symbol"))
                .orderStatus((String) tradeData.get("order_status"))
                .jobStatus((String) tradeData.get("job_status"))
                .lotSize((Integer) tradeData.get("lot_size"))
                .placeOrderType((String) tradeData.get("place_order_type"))
                .userId(userId)
                .build();
    }

    private TradeDto minimalDto(ManualOrderController.Req req,
                                double price,
                                Long userId,
                                String securityId) {
        return TradeDto.builder()
                .securityId(securityId)
                .exchangeSegment("NSE_EQ")
                .transactionType(req.transactionType())
                .quantity(req.quantity())
                .orderType(req.placeOrderType())
                .productType(req.tradeType())
                .price(price)
                .triggerPrice(0.0)
                .afterMarketOrder(false)
                .validity("DAY")
                .amoTime("OPEN")
                .stockName(req.symbol())
                .tradingSymbol(req.symbol())
                .orderStatus("NEW")
                .jobStatus("NEW")
                .userId(userId)
                .build();
    }
}

