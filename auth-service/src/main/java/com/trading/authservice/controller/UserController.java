package com.trading.authservice.controller;



import java.time.Duration;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.authservice.entity.DhanCredential;
import com.trading.authservice.service.CredentialService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.trading.authservice.dtos.DhanCredentialRequest;
import com.trading.authservice.entity.User;
import com.trading.authservice.repository.DhanCredentialRepository;
import com.trading.authservice.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final DhanCredentialRepository dhanCredentialRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final CredentialService credentialService;



    @PostMapping("/dhan-credentials")
    public ResponseEntity<String> saveCredentials(@RequestBody DhanCredentialRequest request, Authentication auth) {
        String username = auth.getName();

        log.info("Storing Dhan credentials for user: {}", username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.error("User {} not found", username);
                    return new UsernameNotFoundException("User not found");
                });

        // Prepare credential object
        DhanCredential credential = dhanCredentialRepository.findByUserId(user.getId())
                .orElse(new DhanCredential());
        credential.setUser(user);
        credential.setAccessToken(request.accessToken());
        credential.setClientId(request.clientId());

        try {
            // 1. Cache in Redis first
            String redisKey = "dhan:user:" + user.getId();
            credentialService.storeDhanCredential(user.getId(), credential);
            log.info("Cached Dhan credentials for user {} in Redis", username);

            // 2. Save to DB
            dhanCredentialRepository.save(credential);
            log.info("Dhan credentials saved to DB for user: {}", username);

        } catch (Exception e) {
            log.error("Failed to store Dhan credentials for user {}: {}", username, e.getMessage());
            throw new RuntimeException("Error saving credentials", e);
        }

        return ResponseEntity.ok("Dhan credentials saved");
    }

    @GetMapping("/dhan-credentials")
    public ResponseEntity<DhanCredentialRequest> getCredentials(Authentication auth) {
        String username = auth.getName();
        log.info("Fetching Dhan credentials for user: {}", username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        try {
            // 1. Try cache
            String redisKey = "dhan:user:" + user.getId();
            String cached =(String) redisTemplate.opsForValue().get(redisKey);
            if (cached != null) {
                DhanCredential credential = objectMapper.readValue(cached, DhanCredential.class);
                log.info("Fetched Dhan credentials from Redis for user: {}", username);
                return ResponseEntity.ok(new DhanCredentialRequest(credential.getClientId(), credential.getAccessToken()));
            }

            // 2. Fallback to DB
            DhanCredential credential = dhanCredentialRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new RuntimeException("Dhan credentials not found"));
            log.info("Fetched Dhan credentials from DB for user: {}", username);

            return ResponseEntity.ok(new DhanCredentialRequest(credential.getClientId(), credential.getAccessToken()));

        } catch (Exception e) {
            throw new RuntimeException("Error fetching credentials", e);
        }
    }

}


