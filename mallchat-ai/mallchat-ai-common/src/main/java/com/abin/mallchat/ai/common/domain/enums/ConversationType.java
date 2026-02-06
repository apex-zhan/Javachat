package com.abin.mallchat.ai.common.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 对话类型枚举
 * 
 * @author zxw
 */
@Getter
@AllArgsConstructor
public enum ConversationType {
    
    /**
     * 聊天总结
     */
    SUMMARY("SUMMARY", "聊天总结"),
    
    /**
     * 智能问答
     */
    QA("QA", "智能问答"),
    
    /**
     * RAG知识问答
     */
    RAG("RAG", "RAG知识问答");
    
    private final String code;
    private final String desc;
}
