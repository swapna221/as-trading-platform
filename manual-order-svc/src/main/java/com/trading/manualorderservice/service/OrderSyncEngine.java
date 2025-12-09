package com.trading.manualorderservice.service;

import com.trading.manualorderservice.dhan.DhanOrderClient;
import com.trading.manualorderservice.dto.DhanOrderBookResponse;
import com.trading.manualorderservice.entity.OrderEntity;
import com.trading.manualorderservice.entity.OrderRole;
import com.trading.manualorderservice.repo.OrderRepository;
import com.trading.shareddto.entity.BrokerUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSyncEngine {

    private final OrderRepository orderRepository;
    private final DhanCredentialService credentialService;
    private final DhanOrderClient dhanOrderClient;

    @Scheduled(fixedDelay = 10000)
    public void syncBrokerOrders() {

        List<OrderEntity> active = orderRepository.findOrdersInTransit();
        if (active.isEmpty()) {
            return;
        }

        log.info("🔄 [SYNC] Checking {} active orders...", active.size());

        Map<Long, List<OrderEntity>> grouped = new HashMap<>();
        for (OrderEntity order : active) {
            grouped.computeIfAbsent(order.getUserId(), id -> new ArrayList<>()).add(order);
        }

        for (var entry : grouped.entrySet()) {

            Long userId = entry.getKey();
            List<OrderEntity> userOrders = entry.getValue();

            BrokerUserDetails creds = credentialService.getDhanCredentialsByUserId(userId);
            if (creds == null) {
                log.error("❌ [SYNC] No creds found for user {}", userId);
                continue;
            }

            List<DhanOrderBookResponse> book = dhanOrderClient.fetchOrderBook(creds);
            if (book == null || book.isEmpty()) {
                log.warn("⚠️ [SYNC] Empty orderbook for user {}", userId);
                continue;
            }

            Map<String, DhanOrderBookResponse> brokerMap = new HashMap<>();
            for (DhanOrderBookResponse b : book) {
                if (b.getOrderId() != null) {
                    brokerMap.put(b.getOrderId(), b);
                }
            }

            for (OrderEntity dbOrder : userOrders) {

                String oid = dbOrder.getBrokerOrderId();
                if (oid == null || oid.isBlank()) {
                    continue;
                }

                DhanOrderBookResponse broker = brokerMap.get(oid);
                if (broker == null) {
                    continue;
                }

                String brokerStatus = safeUpper(broker.getOrderStatus());
                String dbStatus = safeUpper(dbOrder.getOrderStatus());

                if (!Objects.equals(brokerStatus, dbStatus)) {
                    applyBrokerStatus(dbOrder, dbStatus, brokerStatus, broker);
                }

                // 🆕 Always sync SL modification from Dhan app
                syncSlChangesFromBroker(dbOrder, broker);
            }
        }
    }

    private void syncSlChangesFromBroker(OrderEntity dbOrder, DhanOrderBookResponse broker) {

        if (dbOrder.getRole() != OrderRole.STOPLOSS) return;

        boolean changed = false;

        if (!Objects.equals(dbOrder.getSlPrice(), broker.getPrice())) {
            dbOrder.setSlPrice(broker.getPrice());
            changed = true;
        }

        if (!Objects.equals(dbOrder.getTriggerPrice(), broker.getTriggerPrice())) {
            dbOrder.setTriggerPrice(broker.getTriggerPrice());
            changed = true;
        }

        if (changed) {
            dbOrder.setRemark("SL updated from broker (sync-engine)");
            orderRepository.save(dbOrder);

            log.info("🔄 [SYNC] SL updated from broker → local updated sl={} trg={} (child {})",
                    broker.getPrice(), broker.getTriggerPrice(), dbOrder.getId());
        }
    }


    private void applyBrokerStatus(OrderEntity dbOrder,
                                   String oldStatus,
                                   String brokerStatus,
                                   DhanOrderBookResponse broker) {

        log.info("🟢 [SYNC] {} (localId={}) | DB={} → BROKER={}",
                dbOrder.getBrokerOrderId(), dbOrder.getId(), oldStatus, brokerStatus);

        if (dbOrder.getRole() == OrderRole.ENTRY && "CANCELLED".equalsIgnoreCase(brokerStatus)) {
            handleEntryCancelledAtBroker(dbOrder, brokerStatus);
            return;
        }

        dbOrder.setOrderStatus(brokerStatus);
        dbOrder.setRemark("Updated by SyncEngine (brokerStatus=" + brokerStatus + ")");
        orderRepository.save(dbOrder);

        if (dbOrder.getRole() == OrderRole.STOPLOSS || dbOrder.getRole() == OrderRole.TARGET) {
            handleChildOrderStatusChange(dbOrder, brokerStatus);
        }
    }

    private void handleEntryCancelledAtBroker(OrderEntity entry, String brokerStatus) {

        Double oldTrailing = entry.getTrailingPercent();

        entry.setOrderStatus("CANCELLED");
        entry.setTrailingPercent(0.0);
        entry.setHighestLtp(null);
        entry.setLowestLtp(null);

        entry.setRemark("ENTRY cancelled at broker → trailing stopped (sync-engine)");
        orderRepository.save(entry);

        log.info("🛑 [SYNC] ENTRY {} cancelled at broker → trailing stopped (oldTrailing={})",
                entry.getId(), oldTrailing);
    }


    private void handleChildOrderStatusChange(OrderEntity child, String status) {

        Long parentId = child.getParentOrderId();
        if (parentId == null) return;

        OrderEntity parent = orderRepository.findById(parentId).orElse(null);
        if (parent == null) return;

        status = safeUpper(status);

        if (status.equals("TRADED") || status.equals("FILLED") || status.equals("PART_TRADED")) {

            parent.setOrderStatus("COMPLETED");
            parent.setRemark("Closed because child " + child.getRole() + " executed (sync-engine)");
            orderRepository.save(parent);

            log.info("💰 [SYNC] Parent {} completed due to {} execution", parentId, child.getRole());
            return;
        }


        if (child.getRole() == OrderRole.STOPLOSS && status.equals("CANCELLED")) {

            Double oldTrailing = parent.getTrailingPercent();

            parent.setTrailingPercent(0.0);
            parent.setHighestLtp(null);
            parent.setLowestLtp(null);
            parent.setRemark("Trailing disabled: SL cancelled at broker (sync-engine)");

            orderRepository.save(parent);

            log.info("🛑 [SYNC] SL cancelled at broker for parent {} → trailing disabled", parentId);
            return;
        }


        if (child.getRole() == OrderRole.TARGET && status.equals("CANCELLED")) {
            log.info("ℹ️ [SYNC] TARGET cancelled for parent {}", parentId);
        }
    }

    private String safeUpper(String s) {
        return s == null ? null : s.toUpperCase(Locale.ROOT);
    }
}
