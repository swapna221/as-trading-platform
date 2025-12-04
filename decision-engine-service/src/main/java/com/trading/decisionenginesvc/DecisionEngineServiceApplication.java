package com.trading.decisionenginesvc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = { "com.trading.decisionenginesvc", "com.trading.shareddto" })
public class DecisionEngineServiceApplication {
	public static void main(String[] args) {
		SpringApplication.run(DecisionEngineServiceApplication.class, args);
	}	
}
