package com.trading.manualorderservice.kafka;

import com.trading.shareddto.shareddto.OrderEvent;
import com.trading.shareddto.shareddto.TradeDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;



import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import com.trading.shareddto.shareddto.OrderEvent;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ManualTradeProducer {

    private final KafkaTemplate<String, TradeDto> kafkaTemplate;

    @Value("${kafka.topic.manual-trade}")
    private String topic;

    public ManualTradeProducer(KafkaTemplate<String, TradeDto> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishToKafka(TradeDto event) {
        kafkaTemplate.send(topic, event);
        log.info("✅ Published manual order to Kafka for user {}: {}", event.getUserId(), event.getTradingSymbol());
    }
}