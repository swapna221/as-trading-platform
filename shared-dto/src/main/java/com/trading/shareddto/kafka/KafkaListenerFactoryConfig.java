package com.trading.shareddto.kafka;

import com.trading.shareddto.shareddto.TradeDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;

import com.trading.shareddto.shareddto.AstroSignalEvent;
import com.trading.shareddto.shareddto.OrderEvent;
import com.trading.shareddto.shareddto.TrendSignalEvent;

@Configuration
public class KafkaListenerFactoryConfig {

	@Autowired
	private KafkaConsumerFactoryManager factoryManager;

	@Bean("astroSignalListenerContainerFactory")
	ConcurrentKafkaListenerContainerFactory<String, AstroSignalEvent> astroSignalListenerContainerFactory() {
		return factoryManager.createFactory("astro-group", AstroSignalEvent.class);
	}

	@Bean("trendSignalListenerContainerFactory")
	ConcurrentKafkaListenerContainerFactory<String, TrendSignalEvent> trendSignalListenerContainerFactory() {
		return factoryManager.createFactory("trend-group", TrendSignalEvent.class);
	}
	
	@Bean("manualOrderSignalListenerContainerFactory")
	ConcurrentKafkaListenerContainerFactory<String, OrderEvent> manualOrderSignalListenerContainerFactory() {
		return factoryManager.createFactory("manual-order-group", OrderEvent.class);
	}

	@Bean("manualTradeSignalListenerContainerFactory")
	ConcurrentKafkaListenerContainerFactory<String, TradeDto> manualTradeSignalListenerContainerFactory() {
		return factoryManager.createFactory("manual-trade-group", TradeDto.class);
	}

	// Repeat for other event types as needed
}
