package com.trading.shareddto.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
@Slf4j
public class InternalJwtProvider {

    @Value("${internal.jwt.secret}")
    private String secret;

    @Value("${internal.jwt.expiration-millis}")
    private long expirationMillis;

    private Key secretKey;

    @PostConstruct
    public void init() {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateToken(String issuerService) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMillis);
        return Jwts.builder()
                .setSubject("internal")
                .setIssuer(issuerService)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(secretKey).build().parseClaimsJws(token);
            return true;
        } catch (JwtException e) {
            log.warn("Invalid internal token: {}", e.getMessage());
            return false;
        }
    }

    public String getIssuer(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getIssuer();
    }
}
