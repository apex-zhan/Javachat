package com.abin.mallchat.ai.rag.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 文档索引消息
 * 用于RocketMQ异步索引任务
 * 
 * @author zxw
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentIndexingMessage implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 文档ID
     */
    private Long documentId;
    
    /**
     * 文档标题
     */
    private String title;
    
    /**
     * 文档路径
     */
    private String filePath;
    
    /**
     * 文档类型
     */
    private String documentType;
    
    /**
     * 重试次数
     */
    private Integer retryCount = 0;
}
