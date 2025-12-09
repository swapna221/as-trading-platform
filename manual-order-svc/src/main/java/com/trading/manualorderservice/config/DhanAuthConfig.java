package com.trading.manualorderservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "dhan")
public class DhanAuthConfig {

    private String baseUrl;

    private Long defaultUserId;   // <-- REQUIRED (matches default-user-id)
}
