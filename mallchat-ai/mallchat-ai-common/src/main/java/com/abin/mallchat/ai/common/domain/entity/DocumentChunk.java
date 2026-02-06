package com.abin.mallchat.ai.common.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI文档分块实体
 * 
 * @author zxw
 */
@Data
@TableName("ai_document_chunk")
public class DocumentChunk {
    
    /**
     * 分块ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 所属文档ID
     */
    private Long documentId;
    
    /**
     * 分块序号
     */
    private Integer chunkIndex;
    
    /**
     * 分块内容
     */
    private String content;
    
    /**
     * 分块token数量
     */
    private Integer tokenCount;
    
    /**
     * 向量ID（向量数据库中的ID）
     */
    private String vectorId;
    
    /**
     * 元数据（JSON格式，包含来源位置等信息）
     */
    private String metadata;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
