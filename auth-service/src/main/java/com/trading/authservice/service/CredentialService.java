package com.trading.authservice.service;

import com.trading.authservice.entity.DhanCredential;
import com.trading.shareddto.entity.BrokerUserDetails;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class CredentialService {

    private final RedisTemplate<String, Object> redisTemplate;

    public CredentialService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void storeDhanCredential(Long userId, DhanCredential credential) {
        BrokerUserDetails dto = new BrokerUserDetails();
        dto.setClientId(credential.getClientId());
        dto.setAccessToken(credential.getAccessToken());
        redisTemplate.opsForValue().set("dhan:user:" + userId, dto);
    }
    public BrokerUserDetails getDhanCredential(Long userId) {
        return (BrokerUserDetails) redisTemplate.opsForValue().get("dhan:user:" + userId);
    }
}

