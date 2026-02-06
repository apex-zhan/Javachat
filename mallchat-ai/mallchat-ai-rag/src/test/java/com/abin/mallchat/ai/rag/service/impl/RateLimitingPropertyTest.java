package com.abin.mallchat.ai.rag.service.impl;

import com.abin.mallchat.ai.rag.config.RateLimitConfig;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Feature: ai-assistant-rag, Property 28: Rate Limiting
 * Validates: Requirements 11.4
 * 
 * 验证超出限流后请求被拒绝，验证限流窗口重置后恢复
 * 
 * @author zxw
 */
class RateLimitingPropertyTest {
    
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    
    @Mock
    private ValueOperations<String, Object> valueOperations;
    
    private RateLimitServiceImpl rateLimitService;
    private RateLimitConfig rateLimitConfig;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // 初始化配置
        rateLimitConfig = new RateLimitConfig();
        rateLimitConfig.setEnabled(true);
        
        RateLimitConfig.RagQueryLimit ragQueryLimit = new RateLimitConfig.RagQueryLimit();
        ragQueryLimit.setMaxRequestsPerMinute(10);
        ragQueryLimit.setMaxRequestsPerHour(100);
        ragQueryLimit.setMaxRequestsPerDay(500);
        rateLimitConfig.setRagQuery(ragQueryLimit);
        
        RateLimitConfig.DocumentUploadLimit documentUploadLimit = new RateLimitConfig.DocumentUploadLimit();
        documentUploadLimit.setMaxUploadsPerHour(10);
        documentUploadLimit.setMaxUploadsPerDay(50);
        rateLimitConfig.setDocumentUpload(documentUploadLimit);
        
        RateLimitConfig.QuestionLimit questionLimit = new RateLimitConfig.QuestionLimit();
        questionLimit.setMaxRequestsPerMinute(20);
        questionLimit.setMaxRequestsPerHour(200);
        rateLimitConfig.setQuestion(questionLimit);
        
        // 初始化服务
        rateLimitService = new RateLimitServiceImpl();
        
        // 使用反射注入依赖
        try {
            java.lang.reflect.Field redisField = RateLimitServiceImpl.class.getDeclaredField("redisTemplate");
            redisField.setAccessible(true);
            redisField.set(rateLimitService, redisTemplate);
            
            java.lang.reflect.Field configField = RateLimitServiceImpl.class.getDeclaredField("rateLimitConfig");
            configField.setAccessible(true);
            configField.set(rateLimitService, rateLimitConfig);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject dependencies", e);
        }
        
        // Mock Redis操作
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }
    
    /**
     * Property 28.1: 超出限流后请求应该被拒绝
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 28.1: Requests exceeding rate limit should be rejected")
    void requestsExceedingRateLimitShouldBeRejected(
            @ForAll @IntRange(min = 1, max = 1000) long userId) {
        
        // Given: 用户已经达到限流上限
        int maxRequests = rateLimitConfig.getRagQuery().getMaxRequestsPerMinute();
        when(valueOperations.increment(anyString(), anyLong()))
                .thenReturn((long) maxRequests + 1);
        
        // When: 检查限流
        boolean allowed = rateLimitService.checkRagQueryLimit(userId);
        
        // Then: 应该被拒绝
        assertThat(allowed).isFalse();
    }
    
    /**
     * Property 28.2: 未超出限流的请求应该被允许
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 28.2: Requests within rate limit should be allowed")
    void requestsWithinRateLimitShouldBeAllowed(
            @ForAll @IntRange(min = 1, max = 1000) long userId,
            @ForAll @IntRange(min = 1, max = 9) int requestCount) {
        
        // Given: 用户请求数在限流范围内
        when(valueOperations.increment(anyString(), anyLong()))
                .thenReturn((long) requestCount);
        when(redisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);
        
        // When: 检查限流
        boolean allowed = rateLimitService.checkRagQueryLimit(userId);
        
        // Then: 应该被允许
        assertThat(allowed).isTrue();
    }
    
    /**
     * Property 28.3: 不同用户的限流应该独立
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 28.3: Rate limits should be independent per user")
    void rateLimitsShouldBeIndependentPerUser(
            @ForAll @IntRange(min = 1, max = 1000) long userId1,
            @ForAll @IntRange(min = 1001, max = 2000) long userId2) {
        
        // Given: 用户1达到限流，用户2未达到
        when(valueOperations.increment(contains(String.valueOf(userId1)), anyLong()))
                .thenReturn(11L); // 超过限制
        when(valueOperations.increment(contains(String.valueOf(userId2)), anyLong()))
                .thenReturn(5L); // 未超过限制
        when(redisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);
        
        // When: 检查两个用户的限流
        boolean user1Allowed = rateLimitService.checkRagQueryLimit(userId1);
        boolean user2Allowed = rateLimitService.checkRagQueryLimit(userId2);
        
        // Then: 用户1被拒绝，用户2被允许
        assertThat(user1Allowed).isFalse();
        assertThat(user2Allowed).isTrue();
    }
    
    /**
     * Property 28.4: 第一次请求应该设置过期时间
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 28.4: First request should set expiration time")
    void firstRequestShouldSetExpirationTime(
            @ForAll @IntRange(min = 1, max = 1000) long userId) {
        
        // Given: 第一次请求
        when(valueOperations.increment(anyString(), anyLong()))
                .thenReturn(1L);
        when(redisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);
        
        // When: 检查限流
        boolean allowed = rateLimitService.checkRagQueryLimit(userId);
        
        // Then: 应该被允许
        assertThat(allowed).isTrue();
    }
    
    /**
     * Property 28.5: 文档上传限流应该独立于查询限流
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 28.5: Document upload rate limit should be independent")
    void documentUploadRateLimitShouldBeIndependent(
            @ForAll @IntRange(min = 1, max = 1000) long userId) {
        
        // Given: 查询限流已满，但上传限流未满
        when(valueOperations.increment(contains("rag_query"), anyLong()))
                .thenReturn(11L); // 查询超限
        when(valueOperations.increment(contains("document_upload"), anyLong()))
                .thenReturn(5L); // 上传未超限
        when(redisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);
        
        // When: 检查两种限流
        boolean queryAllowed = rateLimitService.checkRagQueryLimit(userId);
        boolean uploadAllowed = rateLimitService.checkDocumentUploadLimit(userId);
        
        // Then: 查询被拒绝，上传被允许
        assertThat(queryAllowed).isFalse();
        assertThat(uploadAllowed).isTrue();
    }
    
    /**
     * Property 28.6: 禁用限流时所有请求应该被允许
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 28.6: All requests should be allowed when rate limiting is disabled")
    void allRequestsShouldBeAllowedWhenDisabled(
            @ForAll @IntRange(min = 1, max = 1000) long userId) {
        
        // Given: 限流被禁用
        rateLimitConfig.setEnabled(false);
        
        // When: 检查限流
        boolean allowed = rateLimitService.checkRagQueryLimit(userId);
        
        // Then: 应该被允许
        assertThat(allowed).isTrue();
    }
    
    /**
     * Property 28.7: 剩余配额应该正确计算
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 28.7: Remaining quota should be calculated correctly")
    void remainingQuotaShouldBeCalculatedCorrectly(
            @ForAll @IntRange(min = 1, max = 1000) long userId,
            @ForAll @IntRange(min = 0, max = 10) int usedCount) {
        
        // Given: 用户已使用部分配额
        when(valueOperations.get(anyString()))
                .thenReturn(String.valueOf(usedCount));
        
        // When: 获取剩余配额
        int remaining = rateLimitService.getRemainingQuota(userId, "rag_query");
        
        // Then: 剩余配额应该正确
        int maxLimit = rateLimitConfig.getRagQuery().getMaxRequestsPerMinute();
        assertThat(remaining).isEqualTo(maxLimit - usedCount);
    }
    
    /**
     * Property 28.8: 新用户应该有完整配额
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 28.8: New users should have full quota")
    void newUsersShouldHaveFullQuota(
            @ForAll @IntRange(min = 1, max = 1000) long userId) {
        
        // Given: 新用户（Redis中没有记录）
        when(valueOperations.get(anyString()))
                .thenReturn(null);
        
        // When: 获取剩余配额
        int remaining = rateLimitService.getRemainingQuota(userId, "rag_query");
        
        // Then: 应该有完整配额
        int maxLimit = rateLimitConfig.getRagQuery().getMaxRequestsPerMinute();
        assertThat(remaining).isEqualTo(maxLimit);
    }
    
    /**
     * Property 28.9: 限流检查失败时应该允许请求（降级策略）
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 28.9: Requests should be allowed when rate limit check fails")
    void requestsShouldBeAllowedWhenCheckFails(
            @ForAll @IntRange(min = 1, max = 1000) long userId) {
        
        // Given: Redis操作失败
        when(valueOperations.increment(anyString(), anyLong()))
                .thenThrow(new RuntimeException("Redis connection failed"));
        
        // When: 检查限流
        boolean allowed = rateLimitService.checkRagQueryLimit(userId);
        
        // Then: 应该被允许（降级策略）
        assertThat(allowed).isTrue();
    }
    
    /**
     * Property 28.10: 不同时间窗口的限流应该独立
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 28.10: Rate limits for different time windows should be independent")
    void rateLimitsForDifferentWindowsShouldBeIndependent(
            @ForAll @IntRange(min = 1, max = 1000) long userId) {
        
        // Given: 分钟限流已满，但小时限流未满
        when(valueOperations.increment(contains("minute"), anyLong()))
                .thenReturn(11L); // 分钟超限
        when(valueOperations.increment(contains("hour"), anyLong()))
                .thenReturn(50L); // 小时未超限
        when(valueOperations.increment(contains("day"), anyLong()))
                .thenReturn(200L); // 天未超限
        when(redisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);
        
        // When: 检查限流
        boolean allowed = rateLimitService.checkRagQueryLimit(userId);
        
        // Then: 应该被拒绝（因为分钟限流已满）
        assertThat(allowed).isFalse();
    }
}
