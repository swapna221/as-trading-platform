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
     * Checks if SL or TARGET has filled → cancel the other leg.
     */
    @Scheduled(fixedDelay = 2000)
    public void runOcoMonitor() {

        var entries = orderRepository.findEntriesForOco();
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

        // DB checks (no broker calls)
        OrderEntity slFilled  = orderRepository.findFilledSlOrder(entryId);
        OrderEntity tgtFilled = orderRepository.findFilledTargetOrder(entryId);

        // Nothing happened yet
        if (slFilled == null && tgtFilled == null) return;

        BrokerUserDetails creds =
                dhanCredentialService.getDhanCredentialsByUserId(entry.getUserId());

        // SL HIT → cancel TARGET
        if (slFilled != null) {

            OrderEntity activeTarget = orderRepository.findActiveTargetOrder(entryId);
            if (activeTarget != null) {
                cancelOtherLeg(activeTarget, creds, "SL HIT → Canceling TARGET");
            }

            entry.setOrderStatus("COMPLETED");
            entry.setRemark("SL hit, target canceled");
            orderRepository.save(entry);

            return;
        }

        // TARGET HIT → cancel SL
        if (tgtFilled != null) {

            OrderEntity activeSl = orderRepository.findActiveSlOrder(entryId);
            if (activeSl != null) {
                cancelOtherLeg(activeSl, creds, "TARGET HIT → Canceling SL");
            }

            entry.setOrderStatus("COMPLETED");
            entry.setRemark("Target hit, SL canceled");
            orderRepository.save(entry);
        }
    }

    private void cancelOtherLeg(OrderEntity order,
                                BrokerUserDetails creds,
                                String reason) {

        try {
            var cancelRes = dhanOrderClient.cancelOrder(creds, order.getBrokerOrderId());

            String status = cancelRes.isOk() ? cancelRes.getStatus() : "CANCEL_FAILED";

            order.setOrderStatus(status);
            order.setRemark(reason + " → " + cancelRes.getRaw());
            orderRepository.save(order);

            log.info("🟢 OCO: {} → Order {} cancelled", order.getRole(), order.getId());

        } catch (Exception e) {

            order.setOrderStatus("CANCEL_FAILED");
            order.setRemark("OCO cancel failed: " + e.getMessage());
            orderRepository.save(order);

            log.error("❌ OCO cancel error for {} order {}: {}",
                    order.getRole(), order.getId(), e.getMessage());
        }
    }
}
