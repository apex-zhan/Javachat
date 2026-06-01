package com.abin.mallchat.ai.assistant.controller;

import com.abin.mallchat.ai.assistant.domain.dto.QuestionRequest;
import com.abin.mallchat.ai.assistant.service.AIAssistantService;
import com.abin.mallchat.ai.common.dao.AIConversationDao;
import com.abin.mallchat.ai.common.domain.entity.AIConversation;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

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

    @Autowired
    private AIConversationDao aiConversationDao;

    /**
     * 智能问答（流式输出）
     * 支持多轮对话：首次请求可不传 conversationId，服务端会生成并在响应中返回
     * 后续请求携带 conversationId 以保持对话上下文
     */
    @PostMapping(value = "/question", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ApiOperation("智能问答")
    public Flux<ServerSentEvent<String>> answerQuestion(@Validated @RequestBody QuestionRequest request) {
        log.info("收到智能问答请求，用户ID: {}, 问题: {}", request.getUserId(), request.getQuestion());

        // 生成或获取会话ID
        String sessionId = request.getConversationId();
        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = "conv_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        final String finalSessionId = sessionId;

        // 先发送 sessionId 事件
        ServerSentEvent<String> sessionEvent = ServerSentEvent.<String>builder()
                .event("session")
                .data(finalSessionId)
                .build();

        // 获取AI流式响应
        Flux<String> answerFlux = aiAssistantService.answerQuestion(request);

        // 将文本流转换为SSE事件流
        Flux<ServerSentEvent<String>> contentEvents = answerFlux
                .map(content -> ServerSentEvent.<String>builder()
                        .event("message")
                        .data(content)
                        .build());

        // 合并 session 事件和内容事件，并添加结束标记
        return Flux.concat(
                        Mono.just(sessionEvent),
                        contentEvents,
                        Mono.just(ServerSentEvent.<String>builder()
                                .event("done")
                                .data("")
                                .build())
                )
                .doOnError(error -> {
                    log.error("智能问答流式响应异常，用户ID: {}", request.getUserId(), error);
                })
                .onErrorResume(error -> Flux.just(
                        ServerSentEvent.<String>builder()
                                .event("error")
                                .data("处理请求时发生错误：" + error.getMessage())
                                .build()
                ));
    }

    /**
     * 获取对话历史
     *
     * @param sessionId 会话ID
     * @return 对话历史列表
     */
    @GetMapping("/history")
    @ApiOperation("获取对话历史")
    public List<AIConversation> getConversationHistory(@RequestParam String sessionId) {
        log.info("获取对话历史，sessionId: {}", sessionId);

        LambdaQueryWrapper<AIConversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AIConversation::getSessionId, sessionId)
                .orderByAsc(AIConversation::getCreateTime)
                .last("LIMIT 50");

        return aiConversationDao.list(wrapper);
    }

    /**
     * 获取用户的所有会话列表
     *
     * @param userId 用户ID
     * @return 会话ID列表（去重）
     */
    @GetMapping("/sessions")
    @ApiOperation("获取用户会话列表")
    public List<String> getUserSessions(@RequestParam Long userId) {
        log.info("获取用户会话列表，userId: {}", userId);

        LambdaQueryWrapper<AIConversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AIConversation::getUserId, userId)
                .isNotNull(AIConversation::getSessionId)
                .groupBy(AIConversation::getSessionId)
                .orderByDesc(AIConversation::getCreateTime)
                .select(AIConversation::getSessionId);

        return aiConversationDao.list(wrapper).stream()
                .map(AIConversation::getSessionId)
                .distinct()
                .toList();
    }
}
