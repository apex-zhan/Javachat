package com.abin.mallchat.ai.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI模块超时配置
 * 
 * @author zxw
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai.timeout")
public class TimeoutConfig {
    
    /**
     * LLM调用超时时间（秒）
     */
    private int llmCallTimeout = 60;
    
    /**
     * 向量检索超时时间（秒）
     */
    private int vectorSearchTimeout = 10;
    
    /**
     * 文档处理超时时间（秒）
     */
    private int documentProcessingTimeout = 300;
    
    /**
     * 文档索引超时时间（秒）
     */
    private int documentIndexingTimeout = 600;
    
    /**
     * RAG查询总超时时间（秒）
     */
    private int ragQueryTimeout = 90;
}
