package com.abin.mallchat.ai.llm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * LLM 配置类
 * 
 * @author abin
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "spring.ai.openai")
public class LLMConfig {
    
    /**
     * OpenAI API Key
     */
    private String apiKey;
    
    /**
     * OpenAI API Base URL
     * 默认：https://api.openai.com
     */
    private String baseUrl = "https://api.openai.com";
    
    /**
     * 默认模型
     */
    private String model = "gpt-3.5-turbo";
    
    /**
     * 连接超时时间（毫秒）
     */
    private Integer connectTimeout = 30000;
    
    /**
     * 读取超时时间（毫秒）
     */
    private Integer readTimeout = 60000;
    
    /**
     * 最大重试次数
     */
    private Integer maxRetries = 3;
    
    /**
     * 重试延迟（毫秒）
     */
    private Integer retryDelay = 1000;
}
