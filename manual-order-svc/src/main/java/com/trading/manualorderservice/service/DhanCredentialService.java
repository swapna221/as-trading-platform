package com.trading.manualorderservice.service;

import com.trading.manualorderservice.cache.DhanCredentialCache;
import com.trading.manualorderservice.config.AuthServiceConfig;
import com.trading.manualorderservice.dto.DhanCredential;
import com.trading.manualorderservice.entity.DhanCredentialEntity;
import com.trading.manualorderservice.repo.DhanCredentialRepository;
import com.trading.manualorderservice.util.JwtUtil;
import com.trading.shareddto.entity.BrokerUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.servlet.http.HttpServletRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class DhanCredentialService {

    private final JwtUtil jwtUtil;
    private final AuthServiceConfig authServiceConfig;
    private final RestTemplate restTemplate;
    private final DhanCredentialCache dhanCredentialCache;
    DhanCredentialRepository dhanCredentialRepository;

    public Long extractUserIdFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new SecurityException("Missing token");
        }
        return jwtUtil.extractUserId(authHeader.substring(7));
    }

    public BrokerUserDetails getDhanCredentials(Long userId, HttpServletRequest request) {
        BrokerUserDetails credsFromCache = dhanCredentialCache.getFromCache(userId);
        if (credsFromCache != null) return credsFromCache;

        log.info("Fetching Dhan credentials from Auth Service for User ID {}", userId);

        String jwtToken = request.getHeader("Authorization").substring(7);
        String url = authServiceConfig.getUrl() + "/api/user/dhan-credentials";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + jwtToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<DhanCredential> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, DhanCredential.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            DhanCredential creds = response.getBody();
            BrokerUserDetails brokerDetails = new BrokerUserDetails();
            brokerDetails.setAccessToken(creds.getAccessToken());
            brokerDetails.setClientId(creds.getClientId());
            brokerDetails.setUserId(userId);

            dhanCredentialCache.saveToCache(userId, creds);
            return brokerDetails;
        }
        return null;
    }

    public BrokerUserDetails getDhanCredentialsByUserId(Long userId) {
        DhanCredential credential = new DhanCredential();

        // 1️⃣ First try cache
        BrokerUserDetails creds = dhanCredentialCache.getFromCache(userId);
        if (creds != null) return creds;

        // 2️⃣ If not in cache → load from DB (your auth-service DB table)
        DhanCredentialEntity stored = dhanCredentialRepository.findByUserId(userId);
        if (stored == null) {
            log.error("❌ No credentials found for user {} in DB", userId);
            return null;
        }

        BrokerUserDetails brokerDetails = new BrokerUserDetails();
        brokerDetails.setAccessToken(stored.getAccessToken());
        brokerDetails.setClientId(stored.getClientId());
        brokerDetails.setUserId(userId);

        credential.setAccessToken(stored.getAccessToken());
        credential.setClientId(stored.getClientId());
        // cache it
        dhanCredentialCache.saveToCache(userId, credential);
        return brokerDetails;
    }

    public BrokerUserDetails getSystemUser() {
        Long systemUserId = 23L; // <-- Choose an ID that always contains valid Dhan credentials

        BrokerUserDetails creds = getDhanCredentialsByUserId(systemUserId);

        if (creds == null) {
            throw new RuntimeException("System Dhan credentials missing for userId=" + systemUserId);
        }

        return creds;
    }


}
