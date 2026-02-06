package com.abin.mallchat.ai.rag.service.impl;

import com.abin.mallchat.ai.rag.config.RateLimitConfig;
import com.abin.mallchat.ai.rag.service.RateLimitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * AI模块限流服务实现（基于Redis）
 * 
 * @author zxw
 */
@Slf4j
@Service
public class RateLimitServiceImpl implements RateLimitService {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private RateLimitConfig rateLimitConfig;
    
    private static final String RATE_LIMIT_PREFIX = "ai:rate_limit:";
    private static final String RAG_QUERY_KEY = "rag_query:";
    private static final String DOCUMENT_UPLOAD_KEY = "document_upload:";
    private static final String QUESTION_KEY = "question:";
    
    @Override
    public boolean checkRagQueryLimit(Long userId) {
        if (!rateLimitConfig.isEnabled()) {
            return true;
        }
        
        String keyPrefix = RATE_LIMIT_PREFIX + RAG_QUERY_KEY + userId + ":";
        
        // 检查每分钟限流
        if (!checkLimit(keyPrefix + "minute", 
                rateLimitConfig.getRagQuery().getMaxRequestsPerMinute(), 
                1, TimeUnit.MINUTES)) {
            log.warn("User {} exceeded RAG query rate limit (per minute)", userId);
            return false;
        }
        
        // 检查每小时限流
        if (!checkLimit(keyPrefix + "hour", 
                rateLimitConfig.getRagQuery().getMaxRequestsPerHour(), 
                1, TimeUnit.HOURS)) {
            log.warn("User {} exceeded RAG query rate limit (per hour)", userId);
            return false;
        }
        
        // 检查每天限流
        if (!checkLimit(keyPrefix + "day", 
                rateLimitConfig.getRagQuery().getMaxRequestsPerDay(), 
                1, TimeUnit.DAYS)) {
            log.warn("User {} exceeded RAG query rate limit (per day)", userId);
            return false;
        }
        
        return true;
    }
    
    @Override
    public boolean checkDocumentUploadLimit(Long userId) {
        if (!rateLimitConfig.isEnabled()) {
            return true;
        }
        
        String keyPrefix = RATE_LIMIT_PREFIX + DOCUMENT_UPLOAD_KEY + userId + ":";
        
        // 检查每小时限流
        if (!checkLimit(keyPrefix + "hour", 
                rateLimitConfig.getDocumentUpload().getMaxUploadsPerHour(), 
                1, TimeUnit.HOURS)) {
            log.warn("User {} exceeded document upload rate limit (per hour)", userId);
            return false;
        }
        
        // 检查每天限流
        if (!checkLimit(keyPrefix + "day", 
                rateLimitConfig.getDocumentUpload().getMaxUploadsPerDay(), 
                1, TimeUnit.DAYS)) {
            log.warn("User {} exceeded document upload rate limit (per day)", userId);
            return false;
        }
        
        return true;
    }
    
    @Override
    public boolean checkQuestionLimit(Long userId) {
        if (!rateLimitConfig.isEnabled()) {
            return true;
        }
        
        String keyPrefix = RATE_LIMIT_PREFIX + QUESTION_KEY + userId + ":";
        
        // 检查每分钟限流
        if (!checkLimit(keyPrefix + "minute", 
                rateLimitConfig.getQuestion().getMaxRequestsPerMinute(), 
                1, TimeUnit.MINUTES)) {
            log.warn("User {} exceeded question rate limit (per minute)", userId);
            return false;
        }
        
        // 检查每小时限流
        if (!checkLimit(keyPrefix + "hour", 
                rateLimitConfig.getQuestion().getMaxRequestsPerHour(), 
                1, TimeUnit.HOURS)) {
            log.warn("User {} exceeded question rate limit (per hour)", userId);
            return false;
        }
        
        return true;
    }
    
    @Override
    public int getRemainingQuota(Long userId, String limitType) {
        if (!rateLimitConfig.isEnabled()) {
            return Integer.MAX_VALUE;
        }
        
        String key = RATE_LIMIT_PREFIX + limitType + ":" + userId + ":minute";
        Object value = redisTemplate.opsForValue().get(key);
        
        if (value == null) {
            return getMaxLimit(limitType);
        }
        
        int currentCount = Integer.parseInt(value.toString());
        int maxLimit = getMaxLimit(limitType);
        
        return Math.max(0, maxLimit - currentCount);
    }
    
    /**
     * 检查限流
     * 
     * @param key Redis键
     * @param maxCount 最大请求数
     * @param duration 时间窗口
     * @param timeUnit 时间单位
     * @return true表示允许请求，false表示超过限流
     */
    private boolean checkLimit(String key, int maxCount, long duration, TimeUnit timeUnit) {
        try {
            // 获取当前计数
            Long currentCount = redisTemplate.opsForValue().increment(key, 1);
            
            if (currentCount == null) {
                return false;
            }
            
            // 如果是第一次请求，设置过期时间
            if (currentCount == 1) {
                redisTemplate.expire(key, duration, timeUnit);
            }
            
            // 检查是否超过限制
            return currentCount <= maxCount;
            
        } catch (Exception e) {
            log.error("Failed to check rate limit for key: {}", key, e);
            // 限流检查失败时，默认允许请求（避免影响正常业务）
            return true;
        }
    }
    
    /**
     * 获取最大限制数
     */
    private int getMaxLimit(String limitType) {
        if (limitType.contains("rag_query")) {
            return rateLimitConfig.getRagQuery().getMaxRequestsPerMinute();
        } else if (limitType.contains("document_upload")) {
            return rateLimitConfig.getDocumentUpload().getMaxUploadsPerHour();
        } else if (limitType.contains("question")) {
            return rateLimitConfig.getQuestion().getMaxRequestsPerMinute();
        }
        return 0;
    }
}
