package com.trading.manualorderservice.service;

import com.trading.manualorderservice.dhan.DhanOrderClient;
import com.trading.manualorderservice.entity.OrderEntity;
import com.trading.manualorderservice.repo.OrderRepository;
import com.trading.shareddto.entity.BrokerUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StatusUpdateEngine {

    private final OrderRepository orderRepository;
    private final DhanOrderClient dhanOrderClient;
    private final DhanCredentialService dhanCredentialService;

    @Scheduled(fixedDelay = 4000)
    public void updateOrderStatuses() {

        List<OrderEntity> transitOrders =
                orderRepository.findOrdersInTransit(); // we will add this

        for (OrderEntity o : transitOrders) {
            try {
                BrokerUserDetails creds =
                        dhanCredentialService.getDhanCredentialsByUserId(o.getUserId());

                if (creds == null) continue;

                var st = dhanOrderClient.getOrderStatus(creds, o.getBrokerOrderId());
                if (!st.isOk()) continue;

                o.setOrderStatus(st.getStatus());
                o.setRemark("Updated by status engine: " + st.getRaw());
                orderRepository.save(o);

                log.info("🔄 Updated {} → {}", o.getId(), st.getStatus());

            } catch (Exception ex) {
                log.error("❌ Failed to update {}", o.getId(), ex);
            }
        }
    }
}

