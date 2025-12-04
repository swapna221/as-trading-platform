package com.trading.decisionenginesvc.consumer;

import com.trading.shareddto.shareddto.TradeDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.trading.shareddto.shareddto.AstroSignalEvent;
import com.trading.shareddto.shareddto.OrderEvent;
import com.trading.shareddto.shareddto.TrendSignalEvent;
import com.trading.decisionenginesvc.service.OrderProcessor;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DecisionEngineKafkaConsumer {

    @Autowired
    private OrderProcessor OrderProcessor;

    @KafkaListener(
            topics = "astro-signals",
            containerFactory = "astroSignalListenerContainerFactory"
    )
    public void consume(AstroSignalEvent event) {
        log.info("Astro signal received: " + event);
    }

    @KafkaListener(
            topics = "trend-signals",
            containerFactory = "trendSignalListenerContainerFactory"
    )
    public void consume(TrendSignalEvent event) {
        log.info("Trend signal received: " + event);
    }

    @KafkaListener(
            topics = "manual-order-topic",
            containerFactory = "manualOrderSignalListenerContainerFactory"
    )
    public void consume(OrderEvent event) {
        log.info("Trend signal received: " + event);
        //OrderProcessor.process(event);
    }

	@KafkaListener(
			topics = "manual-trade-topic",
			containerFactory = "manualTradeSignalListenerContainerFactory"
	)
	public void consume(TradeDto event) {
		log.info("Manual Trade signal received: " + event);
		OrderProcessor.processOrder(event);
	}

    // Add more consumers as needed
}

