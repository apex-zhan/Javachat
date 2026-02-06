package com.abin.mallchat.ai.rag.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;

/**
 * RAG查询请求DTO
 * 
 * @author zxw
 */
@Data
public class RAGQueryRequest {
    
    /**
     * 用户问题
     */
    @NotBlank(message = "问题不能为空")
    @Size(max = 1000, message = "问题长度不能超过1000个字符")
    private String question;
    
    /**
     * 文档ID（可选，null表示全局检索）
     */
    private Long documentId;
    
    /**
     * 用户ID
     */
    @NotNull(message = "用户ID不能为空")
    private Long userId;
    
    /**
     * 检索数量（Top-K）
     */
    @Positive(message = "检索数量必须大于0")
    private Integer topK = 5;
}
