package com.abin.mallchat.common.chatai.controller;

import com.abin.mallchat.ai.assistant.domain.dto.ChatSummaryRequest;
import com.abin.mallchat.common.chatai.service.ChatSummaryService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import javax.validation.Valid;

/**
 * 聊天内容总结控制器
 *
 * @author zxw
 * @since 2025-01-07
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/assistant")
@Api(tags = "AI聊天总结接口")
public class ChatSummaryController {

    @Autowired
    private ChatSummaryService chatSummaryService;

    /**
     * 总结聊天内容（流式输出）
     */
    @PostMapping(value = "/summarize", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ApiOperation("总结聊天内容")
    public Flux<String> summarizeChat(@Valid @RequestBody ChatSummaryRequest request) {
        log.info("收到聊天总结请求: {}", request);
        return chatSummaryService.summarizeChat(request);
    }
}
