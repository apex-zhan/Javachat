package com.abin.mallchat.ai.rag.service.impl;

import com.abin.mallchat.ai.llm.domain.LLMOptions;
import com.abin.mallchat.ai.llm.service.LLMService;
import com.abin.mallchat.ai.rag.service.DegradationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 降级服务实现
 * 
 * @author zxw
 */
@Slf4j
@Service
public class DegradationServiceImpl implements DegradationService {
    
    @Autowired
    private LLMService llmService;
    
    private volatile boolean vectorStoreAvailable = true;
    private volatile long lastCheckTime = System.currentTimeMillis();
    private static final long CHECK_INTERVAL = 60000; // 1分钟检查一次
    
    /**
     * RAG查询降级（向量库不可用时）
     * 降级到普通问答模式，不使用知识库上下文
     */
    @Override
    public Flux<String> degradedRAGQuery(String question) {
        log.warn("RAG service degraded, falling back to normal Q&A mode");
        
        // 构造降级提示 + 问题
        String degradedPrompt = buildDegradedPrompt(question);
        
        try {
            // 使用LLM进行普通问答（不带知识库上下文）
            return llmService.streamChat(degradedPrompt, LLMOptions.builder()
                    .temperature(0.7)
                    .maxTokens(1000)
                    .build());
        } catch (Exception e) {
            log.error("Degraded query also failed", e);
            // 如果LLM也失败，返回最基本的提示
            return Flux.just("抱歉，服务暂时不可用，请稍后再试。");
        }
    }
    
    /**
     * 判断是否应该降级
     * 基于向量库可用性判断
     */
    @Override
    public boolean shouldDegrade() {
        // 定期检查向量库状态
        long now = System.currentTimeMillis();
        if (now - lastCheckTime > CHECK_INTERVAL) {
            // 这里可以添加实际的健康检查逻辑
            // 暂时返回缓存的状态
            lastCheckTime = now;
        }
        
        return !vectorStoreAvailable;
    }
    
    /**
     * 获取降级提示消息
     */
    @Override
    public String getDegradationMessage() {
        return "知识库服务暂时不可用，已切换到普通问答模式。";
    }
    
    /**
     * 构造降级模式的提示词
     * 不包含知识库上下文，但提示用户当前处于降级模式
     */
    private String buildDegradedPrompt(String question) {
        return String.format(
                "你是一个智能助手。由于知识库暂时不可用，请基于你的通用知识回答以下问题。\n\n" +
                "问题：%s\n\n" +
                "请提供有帮助的回答，如果不确定，请诚实地说明。",
                question
        );
    }
    
    /**
     * 设置向量库可用性状态
     * 供外部调用更新状态
     */
    public void setVectorStoreAvailable(boolean available) {
        if (this.vectorStoreAvailable != available) {
            log.info("Vector store availability changed: {} -> {}", 
                    this.vectorStoreAvailable, available);
            this.vectorStoreAvailable = available;
            this.lastCheckTime = System.currentTimeMillis();
        }
    }
    
    /**
     * 获取向量库可用性状态
     */
    public boolean isVectorStoreAvailable() {
        return vectorStoreAvailable;
    }
}
