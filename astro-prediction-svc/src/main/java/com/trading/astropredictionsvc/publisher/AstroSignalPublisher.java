package com.trading.astropredictionsvc.publisher;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.trading.shareddto.shareddto.AstroSignalEvent;



@Service
public class AstroSignalPublisher {

    private final KafkaTemplate<String, AstroSignalEvent> kafkaTemplate;

    public AstroSignalPublisher(KafkaTemplate<String, AstroSignalEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishTrend(AstroSignalEvent event) {
        kafkaTemplate.send("astro-signals", event.getPrediction(), event);
        System.out.println("Published to Kafka: " + event);
    }
}
