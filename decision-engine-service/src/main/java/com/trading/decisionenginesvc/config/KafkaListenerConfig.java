package com.trading.decisionenginesvc.config;

import org.springframework.context.annotation.Configuration;

import com.trading.shareddto.kafka.KafkaConsumerFactoryManager;

@Configuration
public class KafkaListenerConfig {

	private final KafkaConsumerFactoryManager factoryManager;

	public KafkaListenerConfig(KafkaConsumerFactoryManager factoryManager) {
		this.factoryManager = factoryManager;
	}
}
