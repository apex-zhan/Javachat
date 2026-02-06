package com.abin.mallchat.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;

import java.util.concurrent.TimeUnit;

/**
 * Redis高可用配置
 */
@Configuration
public class RedisHighAvailabilityConfig {

    /**
     * Redis Sentinel配置
     */
    @Bean
    @ConfigurationProperties(prefix = "spring.redis.sentinel")
    public RedisSentinelConfiguration redisSentinelConfiguration() {
        return new RedisSentinelConfiguration();
    }

    /**
     * Caffeine缓存配置
     */
    @Bean("tokenCaffeineCache")
    public com.github.benmanes.caffeine.cache.Cache<String, String> tokenCaffeineCache() {
        return Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .expireAfterAccess(2, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

    @Bean("userInfoCaffeineCache")
    public com.github.benmanes.caffeine.cache.Cache<String, String> userInfoCaffeineCache() {
        return Caffeine.newBuilder()
                .maximumSize(5000)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

    @Bean("generalCaffeineCache")
    public com.github.benmanes.caffeine.cache.Cache<String, Object> generalCaffeineCache() {
        return Caffeine.newBuilder()
                .maximumSize(2000)
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .expireAfterAccess(5, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }
}