package com.trading.manualorderservice.market;

import com.trading.manualorderservice.repo.OrderRepository;
import com.trading.manualorderservice.entity.OrderEntity;
import com.trading.manualorderservice.service.DhanAllApis;
import com.trading.manualorderservice.service.DhanCredentialService;
import com.trading.manualorderservice.util.RetryUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class LtpBatchRefreshEngine {

    private final OrderRepository orderRepository;
    private final DhanAllApis dhanAllApis;
    private final DhanCredentialService dhanCredentialService;
    private final LtpCacheService ltpCacheService;

    /** Runs every 5 seconds */
    @Scheduled(fixedDelay = 15000)
    public void refreshLtpCache() {

        // 1️⃣ Get all active security IDs
        List<OrderEntity> orders = orderRepository.findOrdersForLtpRefresh();

        Set<String> secIds = orders.stream()
                .map(OrderEntity::getSecurityId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (secIds.isEmpty()) return;

        // 2️⃣ Pick any valid userId from orders
        Long anyUserId = orders.get(0).getUserId();

        var creds = dhanCredentialService.getDhanCredentialsByUserId(anyUserId);
        if (creds == null) {
            log.error("❌ No Dhan credentials available for LTP refresh!");
            return;
        }

        log.info("📊 Refreshing LTP for {} instruments using user {}", secIds.size(), anyUserId);

        // 3️⃣ Fetch LTP for each security ID
        for (String secId : secIds) {
            try {
                double ltp = RetryUtils.execute(
                        () -> dhanAllApis.fetchLTPStock(Integer.parseInt(secId), creds),
                        "Fetch LTP for " + secId
                );

                if (ltp > 0) {
                    ltpCacheService.update(secId, ltp);
                }

            } catch (Exception e) {
                log.warn("⚠️ Failed to refresh LTP for {}: {}", secId, e.getMessage());
            }
        }
    }
}
