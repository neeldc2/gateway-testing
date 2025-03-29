package com.neel.gateway_testing.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.neel.gateway_testing.dto.ValidateResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    /*@Bean
    public Cache<String, UserContext> jwtCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES) // Default expiry (per-token TTL set in filter)
                .build();
    }*/

    @Bean
    public Cache<String, ValidateResponse> jwtCache() {
        return Caffeine.newBuilder()
                .expireAfter(new Expiry<String, ValidateResponse>() {
                    @Override
                    public long expireAfterCreate(String key, ValidateResponse validateResponse, long currentTime) {
                        long expirySeconds = validateResponse.getExpiryTime() - (System.currentTimeMillis() / 1000);

                        if (expirySeconds <= 0) {
                            return 0;
                        }

                        return TimeUnit.SECONDS.toNanos(expirySeconds);
                    }

                    @Override
                    public long expireAfterUpdate(String key, ValidateResponse value, long currentTime, long currentDuration) {
                        return currentDuration; // Keep original TTL
                    }

                    @Override
                    public long expireAfterRead(String key, ValidateResponse value, long currentTime, long currentDuration) {
                        return currentDuration; // Keep original TTL
                    }
                })
                .build();
    }

}

