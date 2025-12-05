package com.trading.manualorderservice.market;

import com.trading.manualorderservice.entity.OrderEntity;
import com.trading.manualorderservice.repo.OrderRepository;
import com.trading.manualorderservice.service.DhanCredentialService;
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
    private final DhanCredentialService dhanCredentialService;
    private final BatchLtpService batchLtpService;

    @Scheduled(fixedDelay = 20000)
    public void refreshLtpCache() {

        List<OrderEntity> orders = orderRepository.findOrdersForLtpRefresh();
        if (orders.isEmpty()) {
            log.debug("No orders requiring LTP refresh.");
            return;
        }

        // Always use SYSTEM USER for LTP calls
        var creds = dhanCredentialService.getSystemUser();
        if (creds == null) {
            log.error("❌ System credentials missing, cannot refresh LTP");
            return;
        }

        // Group security IDs by exchange segment
        Map<String, Set<String>> segmentsMap = new HashMap<>();

        for (OrderEntity o : orders) {
            String seg = o.getExchangeSegment();
            String sec = o.getSecurityId();

            if (seg == null || sec == null) continue;

            segmentsMap.computeIfAbsent(seg, x -> new HashSet<>()).add(sec);
        }

        if (segmentsMap.isEmpty()) {
            log.warn("⚠ No valid segments found for LTP batching");
            return;
        }

        log.info("📡 Refreshing LTP batch → Segments: {}", segmentsMap);

        // Perform batch call
        batchLtpService.fetchBatchLtp(
                segmentsMap.entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                e -> new ArrayList<>(e.getValue())
                        )),
                creds
        );
    }
}

