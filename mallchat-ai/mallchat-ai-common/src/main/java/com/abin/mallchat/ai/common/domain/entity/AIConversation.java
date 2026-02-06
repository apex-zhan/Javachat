package com.abin.mallchat.ai.common.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI对话历史实体
 * 
 * @author zxw
 */
@Data
@TableName("ai_conversation")
public class AIConversation {
    
    /**
     * 对话ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 会话类型（SUMMARY, QA, RAG）
     */
    private String conversationType;
    
    /**
     * 用户输入
     */
    private String userInput;
    
    /**
     * AI回复
     */
    private String aiResponse;
    
    /**
     * 关联文档ID（RAG场景）
     */
    private Long documentId;
    
    /**
     * 检索到的分块ID列表（JSON数组）
     */
    private String retrievedChunkIds;
    
    /**
     * 响应耗时（毫秒）
     */
    private Long responseTime;
    
    /**
     * 状态（SUCCESS, FAILED, CANCELLED）
     */
    private String status;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
