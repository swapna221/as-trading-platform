package com.trading.manualorderservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "auth-service")
@Primary
public class AuthServiceConfig {
    private String url;
}
