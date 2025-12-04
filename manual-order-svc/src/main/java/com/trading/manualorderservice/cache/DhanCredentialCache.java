package com.trading.manualorderservice.cache;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.manualorderservice.dto.DhanCredential;
import com.trading.shareddto.entity.BrokerUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class DhanCredentialCache {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final Duration EXPIRY = Duration.ofMinutes(30);

    public void saveToCache(Long userId, DhanCredential credential) {
        BrokerUserDetails dto = new BrokerUserDetails();
        dto.setClientId(credential.getClientId());
        dto.setAccessToken(credential.getAccessToken());
        redisTemplate.opsForValue().set("dhan:user:" + userId, dto);
    }
    public BrokerUserDetails getFromCache(Long userId) {
        return (BrokerUserDetails) redisTemplate.opsForValue().get("dhan:user:" + userId);
    }

}

