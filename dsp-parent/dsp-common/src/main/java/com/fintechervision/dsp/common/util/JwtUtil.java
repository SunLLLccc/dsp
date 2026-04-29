package com.fintechervision.dsp.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * JWT工具类
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${dsp.jwt.secret:dsp-default-secret-key-must-be-at-least-32-chars-long}")
    private String secret;

    @Value("${dsp.jwt.expiration-hours:24}")
    private int expirationHours;

    private SecretKey key;

    @PostConstruct
    public void init() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(String appId, List<String> allowedTransnos) {
        long now = System.currentTimeMillis();
        Date issuedAt = new Date(now);
        Date expiration = new Date(now + expirationHours * 3600 * 1000L);

        return Jwts.builder()
                .setIssuedAt(issuedAt)
                .setExpiration(expiration)
                .claim("appId", appId)
                .claim("allowedTransnos", allowedTransnos)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> parseToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return (Map<String, Object>) (Map<?, ?>) claims;
    }
}
