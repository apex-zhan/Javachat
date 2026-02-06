package com.abin.mallchat.ai.rag.service;

import com.abin.mallchat.ai.rag.domain.dto.DocumentIndexingMessage;

/**
 * 文档索引消息生产者接口
 * 
 * @author zxw
 */
public interface DocumentIndexingProducer {
    
    /**
     * 发送文档索引任务消息
     * 
     * @param message 索引消息
     */
    void sendIndexingTask(DocumentIndexingMessage message);
}
