package com.abin.mallchat.ai.llm.service.impl;

import com.abin.mallchat.ai.llm.domain.LLMOptions;
import com.abin.mallchat.ai.llm.service.LLMService;
import dev.langchain4j.data.message.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Mock LLM 服务实现
 *
 * 用于本地开发/测试环境，无需部署真实LLM即可启动项目。
 * 返回预设的模拟回复，支持流式和非流式接口。
 *
 * 启用方式：spring.profiles.active=mock
 *
 * @author abin
 */
@Slf4j
@Service
@Profile("mock")
public class MockLLMService implements LLMService {

    private static final String MOCK_REPLY = "【Mock模式】这是一个模拟回复。当前没有部署真实的LLM服务（如Qwen2.5-14B或Llama3-70B），请通过Ollama部署后切换配置。\n\n您的提问是：%s";

    @Override
    public Flux<String> streamChat(String prompt, LLMOptions options) {
        log.info("[Mock] streamChat called, prompt length: {}", prompt.length());
        String reply = String.format(MOCK_REPLY, prompt);
        // 模拟流式输出：将回复拆分成多个token逐个返回
        return Flux.fromArray(reply.split(""))
                .delayElements(java.time.Duration.ofMillis(10)); // 模拟打字效果
    }

    @Override
    public String chat(String prompt, LLMOptions options) {
        log.info("[Mock] chat called, prompt length: {}", prompt.length());
        return String.format(MOCK_REPLY, prompt);
    }

    @Override
    public Flux<String> streamChat(List<ChatMessage> messages, LLMOptions options) {
        log.info("[Mock] streamChat with messages called, count: {}", messages.size());
        String lastMessage = messages.isEmpty() ? "" : messages.get(messages.size() - 1).text();
        String reply = String.format(MOCK_REPLY, lastMessage);
        return Flux.fromArray(reply.split(""))
                .delayElements(java.time.Duration.ofMillis(10));
    }

    @Override
    public String chat(List<ChatMessage> messages, LLMOptions options) {
        log.info("[Mock] chat with messages called, count: {}", messages.size());
        String lastMessage = messages.isEmpty() ? "" : messages.get(messages.size() - 1).text();
        return String.format(MOCK_REPLY, lastMessage);
    }

    @Override
    public int countTokens(String text) {
        // 简单估算：中文字符约1.5字符/token，英文约4字符/token
        int chinese = 0, other = 0;
        for (char c : text.toCharArray()) {
            if (c >= 0x4E00 && c <= 0x9FA5) chinese++;
            else other++;
        }
        return (int) (chinese / 1.5 + other / 4.0);
    }
}
