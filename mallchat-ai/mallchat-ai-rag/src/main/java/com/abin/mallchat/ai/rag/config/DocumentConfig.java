package com.abin.mallchat.ai.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 文档配置
 * 
 * @author zxw
 */
@Data
@Component
@ConfigurationProperties(prefix = "document")
public class DocumentConfig {
    
    /**
     * 允许的文档格式
     */
    private List<String> allowedFormats = Arrays.asList("txt", "pdf", "md", "html", "docx", "doc");
    
    /**
     * 最大文件大小（字节），默认10MB
     */
    private Long maxFileSize = 10 * 1024 * 1024L;
    
    /**
     * 文档存储路径
     */
    private String storagePath = "/data/documents";
    
    /**
     * 是否使用OSS存储
     */
    private Boolean useOss = false;
    
    /**
     * OSS bucket名称
     */
    private String ossBucket;
}
