package com.abin.mallchat.ai.llm.service;

import com.abin.mallchat.ai.llm.domain.LLMOptions;
import reactor.core.publisher.Flux;

/**
 * LLM Service Interface
 * 封装大语言模型调用逻辑
 * 
 * @author abin
 */
public interface LLMService {
    
    /**
     * 流式调用 LLM
     * 
     * @param prompt 提示词
     * @param options 调用选项（温度、最大 token 等）
     * @return 流式响应
     */
    Flux<String> streamChat(String prompt, LLMOptions options);
    
    /**
     * 非流式调用 LLM
     * 
     * @param prompt 提示词
     * @param options 调用选项
     * @return 完整响应
     */
    String chat(String prompt, LLMOptions options);
    
    /**
     * 计算 token 数量
     * 
     * @param text 文本内容
     * @return token 数量
     */
    int countTokens(String text);
}
