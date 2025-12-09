package com.trading.manualorderservice.service;

import com.trading.manualorderservice.entity.OrderEntity;
import com.trading.manualorderservice.entity.OrderRole;
import com.trading.manualorderservice.market.LtpCacheService;
import com.trading.manualorderservice.repo.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrailingSlEngine {

    private final OrderRepository orderRepository;
    private final LtpCacheService ltpCacheService;

    /**
     * Runs every 5 seconds.
     */
    @Scheduled(fixedDelay = 5000)
    public void run() {

        log.info("⏱ [TRAIL] Tick fired");

        List<OrderEntity> raw = orderRepository.findActiveEntriesWithTrailing();
        if (raw == null || raw.isEmpty()) {
            log.info("🔍 [TRAIL] entries found = 0");
            return;
        }

        // -------------------------------
        // ⭐️ NULL-SAFE DEDUPE GROUPING ⭐️
        // -------------------------------
        List<OrderEntity> entries = raw.stream()
                .collect(Collectors.groupingBy(OrderEntity::getTradingSymbol)) // group by symbol
                .values().stream()
                .map(list -> list.stream()
                        .max(Comparator.comparing(this::extractCreatedAt)) // safe comparator
                        .orElse(null)
                )
                .filter(Objects::nonNull)
                .toList();

        log.info("🔍 [TRAIL] entries found = {}", entries.size());

        for (OrderEntity entry : entries) {
            try {
                processEntry(entry);
            } catch (Exception e) {
                log.error("❌ [TRAIL] Error processing entry {} ({})",
                        entry.getId(), entry.getTradingSymbol(), e);
            }
        }
    }

    /**
     * NULL-safe createdAt extractor (works with LocalDateTime or Date).
     */
    private LocalDateTime extractCreatedAt(OrderEntity o) {
        if (o == null || o.getCreatedAt() == null) {
            return LocalDateTime.MIN;
        }
        // createdAt is Instant → convert
        return LocalDateTime.ofInstant(o.getCreatedAt(), ZoneId.systemDefault());
    }


    /* ----------------------------------------------------------------------
       PROCESS ENTRY LOGIC (unchanged from your working version)
    ---------------------------------------------------------------------- */

    private void processEntry(OrderEntity entry) {

        String symbol = entry.getTradingSymbol();
        Long entryId = entry.getId();

        Double trailingPct = entry.getTrailingPercent();
        if (trailingPct == null || trailingPct <= 0.0) {
            log.debug("⚪ [TRAIL][NO-UPDATE] symbol={} entryId={} reasonCode=TRAILING_DISABLED", symbol, entryId);
            return;
        }

        // SL or TGT filled already → trailing stops
        if (orderRepository.findFilledSlOrder(entryId) != null ||
                orderRepository.findFilledTargetOrder(entryId) != null) {

            log.debug("⚪ [TRAIL][NO-UPDATE] symbol={} entryId={} reasonCode=CHILD_ALREADY_FILLED",
                    symbol, entryId);
            return;
        }

        // Fetch LTP
        Double ltp = ltpCacheService.getFresh(entry.getExchangeSegment(), entry.getSecurityId());
        if (ltp == null || ltp <= 0) {
            log.debug("⚪ [TRAIL][NO-UPDATE] symbol={} entryId={} reasonCode=NO_LTP ltp={}", symbol, entryId, ltp);
            return;
        }

        double entryPrice = safe(entry.getEntryPrice());
        if (entryPrice <= 0) {
            log.debug("⚪ [TRAIL][NO-UPDATE] symbol={} entryId={} reasonCode=NO_ENTRY_PRICE entryPrice={}",
                    symbol, entryId, entryPrice);
            return;
        }

        boolean isLong = "BUY".equalsIgnoreCase(entry.getTransactionType());
        double profitPct = computeProfitPercent(isLong, entryPrice, ltp);

        // Profit below threshold → just update high/low LTP
        if (profitPct < trailingPct) {
            updateWatermarksOnly(entry, isLong, ltp, profitPct, trailingPct);
            return;
        }

        // Compute new SL
        double newSl = computeTrailingSl(entry, isLong, ltp, trailingPct);
        Double currentSl = entry.getSlPrice();

        // If trailing does not improve → skip
        if (currentSl != null) {
            if (isLong && newSl <= currentSl) {
                persistEntryWatermarks(entry, isLong, ltp);
                return;
            }
            if (!isLong && newSl >= currentSl) {
                persistEntryWatermarks(entry, isLong, ltp);
                return;
            }
        }

        // Find active SL order
        OrderEntity activeSl = orderRepository.findActiveSlOrder(entryId);

        // Update entry SL
        entry.setSlPrice(newSl);
        persistEntryWatermarks(entry, isLong, ltp);
        orderRepository.save(entry);

        if (activeSl == null) {
            log.debug("⚪ [TRAIL][NO-UPDATE] symbol={} entryId={} reasonCode=NO_ACTIVE_SL", symbol, entryId);
            return;
        }

        double oldChildSl = safe(activeSl.getSlPrice());

        // Update child STOPLOSS locally
        activeSl.setSlPrice(newSl);
        activeSl.setTriggerPrice(newSl);
        activeSl.setRemark(
                String.format("Trailing SL moved from %.2f → %.2f (entryId=%d)", oldChildSl, newSl, entryId)
        );
        orderRepository.save(activeSl);

        log.info("🟢 [TRAIL][MOVE-SL] symbol={} entryId={} newSl={} ltp={} trailPct={} profitPct={}",
                symbol, entryId, newSl, ltp, trailingPct, profitPct);
    }

    /* ----------------------------------------------------------------------
       Helper methods
    ---------------------------------------------------------------------- */

    private void updateWatermarksOnly(OrderEntity entry,
                                      boolean isLong, double ltp,
                                      double profitPct, double trailingPct) {

        persistEntryWatermarks(entry, isLong, ltp);
        orderRepository.save(entry);

        log.debug("⚪ [TRAIL][NO-UPDATE] symbol={} entryId={} BELOW_THRESHOLD profit={} < trail={} ltp={}",
                entry.getTradingSymbol(), entry.getId(),
                round(profitPct), round(trailingPct), ltp);
    }

    private double computeTrailingSl(OrderEntity entry, boolean isLong,
                                     double ltp, double trailingPct) {

        double basis;

        if (isLong) {
            Double high = entry.getHighestLtp();
            if (high == null || high <= 0) high = entry.getEntryPrice();
            if (ltp > high) high = ltp;
            basis = high;
        } else {
            Double low = entry.getLowestLtp();
            if (low == null || low <= 0) low = entry.getEntryPrice();
            if (ltp < low) low = ltp;
            basis = low;
        }

        return isLong ?
                basis * (1 - trailingPct / 100.0) :
                basis * (1 + trailingPct / 100.0);
    }

    private void persistEntryWatermarks(OrderEntity entry, boolean isLong, double ltp) {

        if (isLong) {
            Double h = entry.getHighestLtp();
            if (h == null || ltp > h) entry.setHighestLtp(ltp);
        } else {
            Double lo = entry.getLowestLtp();
            if (lo == null || ltp < lo) entry.setLowestLtp(ltp);
        }
    }

    private double computeProfitPercent(boolean isLong, double entryPrice, double ltp) {
        return isLong
                ? ((ltp - entryPrice) / entryPrice) * 100
                : ((entryPrice - ltp) / entryPrice) * 100;
    }

    private double safe(Double d) {
        return d == null ? 0 : d;
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
