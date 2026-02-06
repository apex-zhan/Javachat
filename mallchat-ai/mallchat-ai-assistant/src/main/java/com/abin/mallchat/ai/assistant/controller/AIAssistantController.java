package com.abin.mallchat.ai.assistant.controller;

import com.abin.mallchat.ai.assistant.domain.dto.ChatSummaryRequest;
import com.abin.mallchat.ai.assistant.domain.dto.QuestionRequest;
import com.abin.mallchat.ai.assistant.service.AIAssistantService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * AI助手控制器
 *
 * @author zxw
 * @since 2025-01-07
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/assistant")
@Api(tags = "AI助手接口")
public class AIAssistantController {

    @Autowired
    private AIAssistantService aiAssistantService;

    /**
     * 总结聊天内容（流式输出）
     */
    @PostMapping(value = "/summarize", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ApiOperation("总结聊天内容")
    public Flux<String> summarizeChat(@Validated @RequestBody ChatSummaryRequest request) {
        log.info("收到聊天总结请求: {}", request);
        return aiAssistantService.summarizeChat(request);
    }

    /**
     * 智能问答（流式输出）
     */
    @PostMapping(value = "/question", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ApiOperation("智能问答")
    public Flux<String> answerQuestion(@Validated @RequestBody QuestionRequest request) {
        log.info("收到智能问答请求，用户ID: {}, 问题: {}", request.getUserId(), request.getQuestion());
        return aiAssistantService.answerQuestion(request);
    }
}
