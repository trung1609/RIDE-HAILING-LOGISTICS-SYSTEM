package com.trung.userdriverservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final StringRedisTemplate redisTemplate;

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenDurationMs;

    private SecretKey getSigningKey(){
        byte[] encodeKey = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(encodeKey);
    }

    public String generateAndSaveRefreshToken(String phoneNumber) {
        String refreshToken = Jwts.builder()
                .setSubject(phoneNumber)
                .setId(UUID.randomUUID().toString())
                .claim("type", "refresh")
                .signWith(getSigningKey())
                .compact();

        String redisKey = "refresh_token:" + phoneNumber;

        redisTemplate.opsForValue().set(
                redisKey,
                refreshToken,
                refreshTokenDurationMs,
                TimeUnit.MILLISECONDS
        );

        return refreshToken;
    }

    public String getPhoneNumberFromRefreshToken(String token){
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.getSubject();
    }
    public boolean validateRefreshToken(String phoneNumber, String requestRefreshToken) {
        String redisKey = "refresh_token:" + phoneNumber;
        String storedToken = redisTemplate.opsForValue().get(redisKey);

        return storedToken != null && storedToken.equals(requestRefreshToken);
    }

    public void deleteRefreshToken(String phoneNumber) {
        String redisKey = "refresh_token:" + phoneNumber;
        redisTemplate.delete(redisKey);
    }
}