package com.aitrip.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtUtils {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expire}")
    private Long expire;

    public String generateToken(Long userId) {
        Date now = new Date();
        Date expireDate = new Date(now.getTime() + expire * 1000);
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(expireDate)
                .signWith(key)
                .compact();
    }

    public Long getUserIdFromToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Long.valueOf(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT 解析失败: {}", e.getMessage());
            return null;
        }
    }

    public boolean validateToken(String token) {
        return getUserIdFromToken(token) != null;
    }
}
