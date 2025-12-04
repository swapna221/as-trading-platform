package com.trading.candlepatternsvc.service;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.trading.shareddto.shareddto.TrendSignalEvent;


@Service
public class TrendSignalPublisher {

    private final KafkaTemplate<String, TrendSignalEvent> kafkaTemplate;

    public TrendSignalPublisher(KafkaTemplate<String, TrendSignalEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishTrend(TrendSignalEvent event) {
        kafkaTemplate.send("trend-signals", event.getSymbol(), event);
        System.out.println("Published to Kafka: " + event);
    }
}

