package com.trading.manualorderservice.service;

import com.trading.manualorderservice.dhan.DhanOrderClient;
import com.trading.manualorderservice.entity.OrderEntity;
import com.trading.manualorderservice.repo.OrderRepository;
import com.trading.shareddto.entity.BrokerUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class OcoEngine {

    private final OrderRepository orderRepository;
    private final DhanOrderClient dhanOrderClient;
    private final DhanCredentialService dhanCredentialService;

    /**
     * Runs every 2 seconds.
     * Checks if SL or TARGET filled → cancel the other.
     */
    @Scheduled(fixedDelay = 2000)
    public void runOcoMonitor() {

        // All entries where SL or TGT might get filled
        var entries = orderRepository.findActiveEntriesWithTrailing();
        if (entries.isEmpty()) return;

        for (OrderEntity entry : entries) {
            try {
                handleOco(entry);
            } catch (Exception e) {
                log.error("❌ OCO error for entry {}: {}", entry.getId(), e.getMessage());
            }
        }
    }

    private void handleOco(OrderEntity entry) {

        Long entryId = entry.getId();

        // Fetch SL & TARGET status from DB (not from broker)
        OrderEntity slFilled = orderRepository.findFilledSlOrder(entryId);
        OrderEntity tgtFilled = orderRepository.findFilledTargetOrder(entryId);

        if (slFilled == null && tgtFilled == null) return; // nothing happened

        BrokerUserDetails creds =
                dhanCredentialService.getDhanCredentialsByUserId(entry.getUserId());

        // Case 1️⃣ : SL filled → cancel TARGET
        if (slFilled != null) {
            OrderEntity activeTarget = orderRepository.findActiveTargetOrder(entryId);
            if (activeTarget != null) {
                tryCancelOther(activeTarget, creds, "SL HIT → Canceling TARGET");
            }

            // Mark entry closed
            entry.setOrderStatus("COMPLETED");
            entry.setRemark("SL hit, target canceled");
            orderRepository.save(entry);
            return;
        }

        // Case 2️⃣ : TARGET filled → cancel SL
        if (tgtFilled != null) {
            OrderEntity activeSl = orderRepository.findActiveSlOrder(entryId);
            if (activeSl != null) {
                tryCancelOther(activeSl, creds, "TARGET HIT → Canceling SL");
            }

            entry.setOrderStatus("COMPLETED");
            entry.setRemark("Target hit, SL canceled");
            orderRepository.save(entry);
        }
    }

    private void tryCancelOther(OrderEntity order, BrokerUserDetails creds, String reason) {

        try {
            var cancelRes = dhanOrderClient.cancelOrder(creds, order.getBrokerOrderId());

            String status = cancelRes.isOk() ? cancelRes.getStatus() : "CANCEL_FAILED";
            order.setOrderStatus(status);
            order.setRemark(reason + " → " + cancelRes.getRaw());
            orderRepository.save(order);

            log.info("🟢 OCO: {} → Order {} canceled successfully",
                    order.getRole(), order.getId());

        } catch (Exception e) {
            log.error("❌ Failed to cancel {} order {}: {}", order.getRole(), order.getId(), e.getMessage());
            order.setOrderStatus("CANCEL_FAILED");
            order.setRemark("OCO cancel failed: " + e.getMessage());
            orderRepository.save(order);
        }
    }
}

