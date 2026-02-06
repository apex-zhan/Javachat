package com.abin.mallchat.ai.rag.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档上传响应DTO
 * 
 * @author zxw
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUploadResponse {
    
    /**
     * 文档ID
     */
    private Long documentId;
    
    /**
     * 文档标题
     */
    private String title;
    
    /**
     * 索引状态
     */
    private String indexStatus;
    
    /**
     * 消息
     */
    private String message;
}
