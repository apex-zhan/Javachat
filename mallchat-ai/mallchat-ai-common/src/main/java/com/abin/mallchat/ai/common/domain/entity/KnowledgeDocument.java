package com.abin.mallchat.ai.common.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI知识文档实体
 * 
 * @author zxw
 */
@Data
@TableName("ai_knowledge_document")
public class KnowledgeDocument {
    
    /**
     * 文档ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 文档标题
     */
    private String title;
    
    /**
     * 文档类型（txt, pdf, md, html, docx）
     */
    private String documentType;
    
    /**
     * 文档大小（字节）
     */
    private Long fileSize;
    
    /**
     * 文档存储路径（OSS）
     */
    private String filePath;
    
    /**
     * 文档内容（小文件直接存储）
     */
    private String content;
    
    /**
     * 索引状态（PENDING, INDEXING, COMPLETED, FAILED）
     */
    private String indexStatus;
    
    /**
     * 分块数量
     */
    private Integer chunkCount;
    
    /**
     * 上传用户ID
     */
    private Long uploadUserId;
    
    /**
     * 错误信息（索引失败时）
     */
    private String errorMessage;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
