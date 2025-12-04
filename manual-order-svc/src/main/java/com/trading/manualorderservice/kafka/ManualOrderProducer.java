package com.trading.manualorderservice.kafka;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import com.trading.shareddto.shareddto.OrderEvent;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ManualOrderProducer {

    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    @Value("${kafka.topic.manual-order}")
    private String topic;

    public ManualOrderProducer(KafkaTemplate<String, OrderEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishToKafka(OrderEvent event) {
        kafkaTemplate.send(topic, event);
        log.info("✅ Published manual order to Kafka for user {}: {}", event.getUserId(), event.getUnderlying());
    }
}
