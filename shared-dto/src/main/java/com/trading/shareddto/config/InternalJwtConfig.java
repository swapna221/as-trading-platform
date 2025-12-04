package com.trading.shareddto.config;

import com.trading.shareddto.security.InternalJwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InternalJwtConfig {

	@Value("${internal.jwt.secret}")
    private String secret;

    @Value("${internal.jwt.expiration-millis}")
    private long expirationMs;

    @Bean
    InternalJwtUtil internalJwtUtil() {
        return new InternalJwtUtil(secret, expirationMs);
    }
}
