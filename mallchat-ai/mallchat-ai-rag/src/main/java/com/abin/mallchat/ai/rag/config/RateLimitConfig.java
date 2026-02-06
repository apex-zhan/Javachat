package com.abin.mallchat.ai.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI模块限流配置
 * 
 * @author zxw
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai.rate-limit")
public class RateLimitConfig {
    
    /**
     * 是否启用限流
     */
    private boolean enabled = true;
    
    /**
     * RAG查询限流配置
     */
    private RagQueryLimit ragQuery = new RagQueryLimit();
    
    /**
     * 文档上传限流配置
     */
    private DocumentUploadLimit documentUpload = new DocumentUploadLimit();
    
    /**
     * 智能问答限流配置
     */
    private QuestionLimit question = new QuestionLimit();
    
    @Data
    public static class RagQueryLimit {
        /**
         * 每个用户每分钟最大请求数
         */
        private int maxRequestsPerMinute = 10;
        
        /**
         * 每个用户每小时最大请求数
         */
        private int maxRequestsPerHour = 100;
        
        /**
         * 每个用户每天最大请求数
         */
        private int maxRequestsPerDay = 500;
    }
    
    @Data
    public static class DocumentUploadLimit {
        /**
         * 每个用户每小时最大上传数
         */
        private int maxUploadsPerHour = 10;
        
        /**
         * 每个用户每天最大上传数
         */
        private int maxUploadsPerDay = 50;
    }
    
    @Data
    public static class QuestionLimit {
        /**
         * 每个用户每分钟最大请求数
         */
        private int maxRequestsPerMinute = 20;
        
        /**
         * 每个用户每小时最大请求数
         */
        private int maxRequestsPerHour = 200;
    }
}
