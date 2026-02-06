package com.abin.mallchat.ai.vector.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 向量检索结果
 * 
 * @author abin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {
    
    /**
     * 文档分块 ID
     */
    private Long chunkId;
    
    /**
     * 分块内容
     */
    private String content;
    
    /**
     * 相似度分数（越高越相似）
     */
    private Float score;
    
    /**
     * 元数据（JSON 格式的额外信息）
     */
    private Map<String, Object> metadata;
    
    /**
     * 文档 ID
     */
    private Long documentId;
    
    /**
     * 分块序号
     */
    private Integer chunkIndex;
}
