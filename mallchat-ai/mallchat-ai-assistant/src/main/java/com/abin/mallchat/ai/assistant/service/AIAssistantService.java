package com.abin.mallchat.ai.assistant.service;

import com.abin.mallchat.ai.assistant.domain.dto.ChatSummaryRequest;
import com.abin.mallchat.ai.assistant.domain.dto.QuestionRequest;
import reactor.core.publisher.Flux;

/**
 * AI助手服务接口
 *
 * @author zxw
 * @since 2025-01-07
 */
public interface AIAssistantService {

    /**
     * 智能问答（流式）
     *
     * @param request 问答请求
     * @return 流式响应
     */
    Flux<String> answerQuestion(QuestionRequest request);
}
