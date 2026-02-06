package com.abin.mallchat.ai.rag.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 文档分块元数据
 * 用于存储分块的来源位置等信息
 * 
 * @author zxw
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChunkMetadata {
    
    /**
     * 分块内容
     */
    private String content;
    
    /**
     * token数量
     */
    private Integer tokenCount;
    
    /**
     * 所属文档ID
     */
    private Long documentId;
    
    /**
     * 分块序号
     */
    private Integer chunkIndex;
    
    /**
     * 来源位置（在原文档中的起始位置）
     */
    private Integer sourceLocation;
    
    /**
     * 来源位置结束（在原文档中的结束位置）
     */
    private Integer sourceLocationEnd;
    
    /**
     * 文档标题
     */
    private String documentTitle;
    
    /**
     * 文档类型
     */
    private String documentType;
    
    /**
     * 分块策略
     */
    private String chunkStrategy;
    
    /**
     * 创建时间戳
     */
    private Long createTimestamp;
    
    /**
     * 额外的元数据信息
     */
    private Map<String, Object> metadata;
    
    /**
     * 转换为JSON字符串
     */
    public String toJson() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize chunk metadata to JSON", e);
        }
    }
    
    /**
     * 从JSON字符串解析
     */
    public static ChunkMetadata fromJson(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(json, ChunkMetadata.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize chunk metadata from JSON", e);
        }
    }
}
