package com.abin.mallchat.config;

import com.alicp.jetcache.anno.config.EnableCreateCacheAnnotation;
import com.alicp.jetcache.anno.config.EnableMethodCache;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 负责 JetCache 的配置和缓存服务的通用封装。
 */
@Configuration(proxyBeanMethods = false) // 标识为配置类，禁用代理bean方法以提高性能
@EnableConfigurationProperties(RedisProperties.class)// 启用Redis属性配置类，允许通过application.properties或application.yml配置Redis属性
//@ConditionalOnExpression("${redis.enabled}") // 使用条件表达式，确保Redis仅在启用时才加载配置
//@ConditionalOnProperty(value = "cache.type", havingValue = "cache")
@EnableMethodCache(basePackages = "com.abin.mallchat") // 开启方法缓存
@EnableCreateCacheAnnotation //开启 @CreateCache 注解支持 (用于编程式缓存)

public class JetCacheConfig {
    // 如果需要自定义 Bean，例如自定义序列化器，可以在这里定义
    // 通常情况下，简单的配置只需要上面的注解即可
}
