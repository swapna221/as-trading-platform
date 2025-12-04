package com.trading.shareddto.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import java.security.Key;
import java.util.Date;

public class InternalJwtUtil {

    private final Key secretKey;
    private final long expirationMillis;

    public InternalJwtUtil(String secret, long expirationMillis) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.expirationMillis = expirationMillis;
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
        } catch (JwtException ex) {
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
