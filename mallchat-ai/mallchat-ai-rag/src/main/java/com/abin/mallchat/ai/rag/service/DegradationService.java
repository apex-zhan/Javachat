package com.abin.mallchat.ai.rag.service;

import reactor.core.publisher.Flux;

/**
 * 降级服务接口
 * 
 * 职责：
 * 1. 提供服务降级逻辑
 * 2. 当向量库不可用时降级到普通问答
 * 3. 当LLM API失败时返回友好提示
 * 
 * @author zxw
 */
public interface DegradationService {
    
    /**
     * RAG查询降级（向量库不可用时）
     * 降级到普通问答模式
     * 
     * @param question 用户问题
     * @return 流式响应
     */
    Flux<String> degradedRAGQuery(String question);
    
    /**
     * 判断是否应该降级
     * 
     * @return true表示应该降级
     */
    boolean shouldDegrade();
    
    /**
     * 获取降级提示消息
     * 
     * @return 降级提示
     */
    String getDegradationMessage();
}
