package com.trung.pricingservice.repository;

import com.trung.pricingservice.entity.PricingConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class PricingConfigRepository {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String REDIS_KEY = "pricing:config";

    public PricingConfig getBasePricingConfig() {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(REDIS_KEY);

        if (entries.isEmpty()) {
            // Cấu hình fallback mặc định nếu Redis trống
            return PricingConfig.builder()
                    .baseFare(15000.0)
                    .pricePerKm(15000.0)
                    .pricePerMinute(1000.0)
                    .build();
        }

        return PricingConfig.builder()
                .baseFare(Double.parseDouble(entries.get("baseFare").toString()))
                .pricePerKm(Double.parseDouble(entries.get("pricePerKm").toString()))
                .pricePerMinute(Double.parseDouble(entries.get("pricePerMinute").toString()))
                .build();
    }

    public void updatePricingConfig(PricingConfig config) {
        Map<String, String> map = new HashMap<>();
        map.put("baseFare", String.valueOf(config.getBaseFare()));
        map.put("pricePerKm", String.valueOf(config.getPricePerKm()));
        map.put("pricePerMinute", String.valueOf(config.getPricePerMinute()));

        redisTemplate.opsForHash().putAll(REDIS_KEY, map);
    }

    public void updateDemand(String h3Index) {
        String key = "pricing:demand:" + h3Index;
        redisTemplate.opsForValue().increment(key, 1);
        // Tự động hết hạn sau 5 phút để refresh cung cầu liên tục
        redisTemplate.expire(key, Duration.ofMinutes(5));
    }

    public int getDemand(String h3Index) {
        Object val = redisTemplate.opsForValue().get("pricing:demand:" + h3Index);
        return val != null ? Integer.parseInt(val.toString()) : 0;
    }
}