package com.trading.manualorderservice.service;

import com.trading.manualorderservice.dhan.DhanOrderClient;
import com.trading.manualorderservice.dhan.ExchangeSegment;
import com.trading.manualorderservice.entity.OrderEntity;
import com.trading.manualorderservice.entity.OrderRole;
import com.trading.manualorderservice.market.LtpCacheService;
import com.trading.manualorderservice.repo.OrderRepository;
import com.trading.manualorderservice.stockfilter.DhanStockHelper;
import com.trading.shareddto.entity.BrokerUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class TrailingSlEngine {

    private final OrderRepository orderRepository;
    private final DhanCredentialService dhanCredentialService;
    private final DhanOrderClient dhanOrderClient;
    private final DhanStockHelper dhanStockHelper;
    private final LtpCacheService ltpCacheService;   // <-- IMPORTANT: use cache

    /**
     * Trailing engine tick:
     * Runs every 3 seconds.
     */
    @Scheduled(fixedDelay = 3000)
    public void runTrailingForIntradayAndOptions() {

        List<OrderEntity> entries = orderRepository.findActiveEntriesWithTrailing();
        if (entries.isEmpty()) return;

        for (OrderEntity entry : entries) {
            try {
                processEntry(entry);
            } catch (Exception e) {
                log.error("❌ Trailing error for entry {}: {}", entry.getId(), e.getMessage(), e);
            }
        }
    }

    private void processEntry(OrderEntity entry) {

        // 1️⃣ Credentials
        BrokerUserDetails creds = dhanCredentialService.getDhanCredentialsByUserId(entry.getUserId());
        if (creds == null) {
            log.error("❌ No creds for user {}. Skipping trailing for entry {}", entry.getUserId(), entry.getId());
            return;
        }

        // 2️⃣ Find SL order
        OrderEntity slOrder = orderRepository.findActiveSlOrder(entry.getId());
        if (slOrder == null) return;

        // 3️⃣ FETCH LTP FROM CACHE ONLY (NEVER CALL DHAN DIRECTLY)
        Double ltp = ltpCacheService.getFresh(entry.getExchangeSegment(), entry.getSecurityId());

        if (ltp == null || ltp <= 0) {
            log.debug("⏭ No fresh LTP for secId={}, skipping trailing", entry.getSecurityId());
            return;
        }

        double entryPrice = entry.getEntryPrice() != null ? entry.getEntryPrice() : 0.0;
        double trailingPct = entry.getTrailingPercent() != null ? entry.getTrailingPercent() : 0.0;
        if (entryPrice <= 0 || trailingPct <= 0) return;

        // 4️⃣ Profit %
        double profitPct = "BUY".equalsIgnoreCase(entry.getTransactionType())
                ? ((ltp - entryPrice) / entryPrice) * 100.0
                : ((entryPrice - ltp) / entryPrice) * 100.0;

        if (profitPct < trailingPct) return;

        // 5️⃣ Trailing SL logic
        double effectiveTrail = profitPct - trailingPct;
        double rawNewSl;

        if ("BUY".equalsIgnoreCase(entry.getTransactionType())) {
            rawNewSl = entryPrice * (1 + effectiveTrail / 100.0);
        } else {
            rawNewSl = entryPrice * (1 - effectiveTrail / 100.0);
        }

        double tick = resolveTickSize(entry);
        double newSl = roundToTick(rawNewSl, tick);

        Double currentSl = slOrder.getSlPrice() != null ? slOrder.getSlPrice() : 0.0;

        // Skip if not better SL
        if ("BUY".equalsIgnoreCase(entry.getTransactionType())) {
            if (newSl <= currentSl) return;
        } else {
            if (newSl >= currentSl) return;
        }

        log.info("🔵 Trailing SL update: {} oldSl={} → newSl={} ltp={} profit%={}",
                entry.getTradingSymbol(), currentSl, newSl, ltp, profitPct);

        // 6️⃣ Cancel old SL
        if (slOrder.getBrokerOrderId() != null) {
            var cancel = dhanOrderClient.cancelOrder(creds, slOrder.getBrokerOrderId());
            slOrder.setOrderStatus(cancel.isOk() ? "CANCELLED" : "CANCEL_FAILED");
            slOrder.setRemark("Cancelled for trailing: " + cancel.getRaw());
        } else {
            slOrder.setOrderStatus("CANCELLED");
            slOrder.setRemark("Local cancel for trailing");
        }
        orderRepository.save(slOrder);

        // 7️⃣ Place new SL order
        double trigger = "BUY".equalsIgnoreCase(entry.getTransactionType())
                ? newSl + tick
                : newSl - tick;

        trigger = roundToTick(trigger, tick);

        var slRes = dhanOrderClient.placeOrder(
                creds,
                entry.getExchangeSegment(),
                reverse(entry.getTransactionType()),
                entry.getProductType(),
                "STOP_LOSS",
                entry.getSecurityId(),
                entry.getQuantity(),
                newSl,
                trigger
        );

        if (!slRes.isOk()) {
            log.error("❌ Failed to place new trailing SL: {}", slRes.getRaw());
            return;
        }

        // 8️⃣ Save new SL order
        OrderEntity newRecord = OrderEntity.builder()
                .userId(entry.getUserId())
                .workflow(entry.getWorkflow())
                .symbol(entry.getSymbol())
                .tradingSymbol(entry.getTradingSymbol())
                .securityId(entry.getSecurityId())
                .exchangeSegment(entry.getExchangeSegment())
                .transactionType(reverse(entry.getTransactionType()))
                .quantity(entry.getQuantity())
                .orderType("STOP_LOSS")
                .productType(entry.getProductType())
                .role(OrderRole.STOPLOSS)
                .parentOrderId(entry.getId())
                .slPrice(newSl)
                .orderStatus(slRes.getStatus())
                .brokerOrderId(slRes.getOrderId())
                .remark("Trailing SL placed: " + slRes.getRaw())
                .build();

        orderRepository.save(newRecord);

        log.info("🟢 New trailing SL placed: {} (trigger={})", newSl, trigger);
    }

    private String reverse(String side) {
        return "BUY".equalsIgnoreCase(side) ? "SELL" : "BUY";
    }

    private double roundToTick(double price, double tick) {
        BigDecimal p = BigDecimal.valueOf(price);
        BigDecimal t = BigDecimal.valueOf(tick);
        return p.divide(t, 0, RoundingMode.HALF_UP).multiply(t).doubleValue();
    }

    private double resolveTickSize(OrderEntity entry) {
        if (ExchangeSegment.NSE_EQ.equalsIgnoreCase(entry.getExchangeSegment())) {
            return dhanStockHelper.getTickSize(entry.getSymbol()).orElse(0.05);
        }
        return 0.05;
    }
}
