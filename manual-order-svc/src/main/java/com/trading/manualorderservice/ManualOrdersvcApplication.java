
package com.trading.manualorderservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

import com.trading.manualorderservice.config.AuthServiceConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
//@EnableConfigurationProperties(AuthServiceConfig.class)
public class ManualOrdersvcApplication {
    public static void main(String[] args) {
        SpringApplication.run(ManualOrdersvcApplication.class, args);
    }

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5); // you may increase to 10 if many scheduled tasks
        scheduler.setThreadNamePrefix("sched-");
        scheduler.initialize();
        return scheduler;
    }
}
