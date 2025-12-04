
package com.trading.manualorderservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

import com.trading.manualorderservice.config.AuthServiceConfig;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
//@EnableConfigurationProperties(AuthServiceConfig.class)
public class ManualOrdersvcApplication {
    public static void main(String[] args) {
        SpringApplication.run(ManualOrdersvcApplication.class, args);
    }
}
