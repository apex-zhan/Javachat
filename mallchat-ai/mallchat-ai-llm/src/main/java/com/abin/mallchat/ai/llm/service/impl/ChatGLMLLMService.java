package com.abin.mallchat.ai.llm.service.impl;

import com.abin.mallchat.ai.llm.domain.LLMOptions;
import com.abin.mallchat.ai.llm.exception.LLMApiException;
import com.abin.mallchat.ai.llm.service.LLMService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;

/**
 * ChatGLM LLM 服务实现（基于 LangChain4j）
 * 
 * 支持智谱AI的ChatGLM模型
 * 
 * 配置示例：
 * langchain4j:
 *   llm:
 *     provider: chatglm
 *   chatglm:
 *     api-key: your-api-key
 *     base-url: https://open.bigmodel.cn/api/paas/v4
 *     model-name: glm-4
 *     timeout: 60s
 *     max-retries: 3
 * 
 * @author zxw
 */
@Slf4j
@Service
@Profile("!mock")
@ConditionalOnProperty(name = "langchain4j.llm.provider", havingValue = "chatglm")
public class ChatGLMLLMService implements LLMService {
    
    @Value("${langchain4j.chatglm.api-key}")
    private String apiKey;
    
    @Value("${langchain4j.chatglm.base-url:https://open.bigmodel.cn/api/paas/v4}")
    private String baseUrl;
    
    @Value("${langchain4j.chatglm.model-name:glm-4}")
    private String modelName;
    
    @Value("${langchain4j.chatglm.timeout:60s}")
    private Duration timeout;
    
    @Value("${langchain4j.chatglm.max-retries:3}")
    private Integer maxRetries;
    
    private ChatLanguageModel chatModel;
    private StreamingChatLanguageModel streamingChatModel;
    
    /**
     * 初始化 ChatGLM 模型
     * 
     * 注意：LangChain4j 0.27.1 可能不直接支持 ChatGLM
     * 这里提供一个框架，实际使用时需要根据 LangChain4j 的支持情况调整
     * 或者使用 ChatGLM 的官方 SDK
     */
    @PostConstruct
    public void init() {
        log.info("Initializing ChatGLM Model: {}", modelName);
        
        // TODO: 根据 LangChain4j 的实际支持情况实现
        // 如果 LangChain4j 不支持 ChatGLM，可以：
        // 1. 使用 ChatGLM 官方 SDK
        // 2. 使用 OpenAI 兼容接口（如果 ChatGLM 提供）
        // 3. 自定义实现 ChatLanguageModel 接口
        
        log.warn("ChatGLM integration is not fully implemented yet");
        log.warn("Please implement ChatGLM model initialization based on available SDK");
        
        // 示例：如果 ChatGLM 提供 OpenAI 兼容接口
        // this.chatModel = OpenAiChatModel.builder()
        //         .apiKey(apiKey)
        //         .baseUrl(baseUrl)
        //         .modelName(modelName)
        //         .timeout(timeout)
        //         .maxRetries(maxRetries)
        //         .build();
        
        log.info("ChatGLM Model initialized (placeholder)");
    }
    
    @Override
    @Retryable(
            value = {LLMApiException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public Flux<String> streamChat(String prompt, LLMOptions options) {
        log.info("ChatGLM stream chat called with prompt length: {}", prompt.length());
        
        if (streamingChatModel == null) {
            log.error("ChatGLM streaming model not initialized");
            return Flux.error(new LLMApiException("ChatGLM streaming model not initialized"));
        }
        
        return Flux.create(sink -> {
            try {
                streamingChatModel.generate(prompt, new StreamingResponseHandler<AiMessage>() {
                    @Override
                    public void onNext(String token) {
                        sink.next(token);
                    }
                    
                    @Override
                    public void onComplete(Response<AiMessage> response) {
                        sink.complete();
                    }
                    
                    @Override
                    public void onError(Throwable error) {
                        log.error("ChatGLM streaming error", error);
                        sink.error(new LLMApiException("ChatGLM API call failed", error));
                    }
                });
            } catch (Exception e) {
                log.error("ChatGLM stream chat failed", e);
                sink.error(new LLMApiException("ChatGLM stream chat failed", e));
            }
        }, FluxSink.OverflowStrategy.BUFFER);
    }
    
    @Override
    @Retryable(
            value = {LLMApiException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public String chat(String prompt, LLMOptions options) {
        log.info("ChatGLM chat called with prompt length: {}", prompt.length());
        
        if (chatModel == null) {
            log.error("ChatGLM model not initialized");
            throw new LLMApiException("ChatGLM model not initialized");
        }
        
        try {
            String response = chatModel.generate(prompt);
            log.debug("ChatGLM response length: {}", response.length());
            return response;
            
        } catch (Exception e) {
            log.error("ChatGLM chat failed", e);
            throw new LLMApiException("ChatGLM API call failed", e);
        }
    }
    
    @Override
    @Retryable(
            value = {LLMApiException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public Flux<String> streamChat(List<ChatMessage> messages, LLMOptions options) {
        log.info("ChatGLM stream chat with messages called, count: {}", messages.size());

        if (streamingChatModel == null) {
            log.error("ChatGLM streaming model not initialized");
            return Flux.error(new LLMApiException("ChatGLM streaming model not initialized"));
        }

        return Flux.create(sink -> {
            try {
                streamingChatModel.generate(messages, new StreamingResponseHandler<AiMessage>() {
                    @Override
                    public void onNext(String token) {
                        sink.next(token);
                    }

                    @Override
                    public void onComplete(Response<AiMessage> response) {
                        sink.complete();
                    }

                    @Override
                    public void onError(Throwable error) {
                        log.error("ChatGLM streaming error", error);
                        sink.error(new LLMApiException("ChatGLM API call failed", error));
                    }
                });
            } catch (Exception e) {
                log.error("ChatGLM stream chat with messages failed", e);
                sink.error(new LLMApiException("ChatGLM stream chat failed", e));
            }
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    @Override
    @Retryable(
            value = {LLMApiException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public String chat(List<ChatMessage> messages, LLMOptions options) {
        log.info("ChatGLM chat with messages called, count: {}", messages.size());

        if (chatModel == null) {
            log.error("ChatGLM model not initialized");
            throw new LLMApiException("ChatGLM model not initialized");
        }

        try {
            Response<AiMessage> response = chatModel.generate(messages);
            String responseText = response.content().text();
            log.debug("ChatGLM response length: {}", responseText.length());
            return responseText;

        } catch (Exception e) {
            log.error("ChatGLM chat with messages failed", e);
            throw new LLMApiException("ChatGLM API call failed", e);
        }
    }

    @Override
    public int countTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        // ChatGLM 的 token 计算
        // 简单估算：中文约1.5字符/token，英文约4字符/token
        // 实际使用时应该使用 ChatGLM 提供的 tokenizer
        
        int chineseChars = 0;
        int otherChars = 0;
        
        for (char c : text.toCharArray()) {
            if (c >= 0x4E00 && c <= 0x9FA5) {
                chineseChars++;
            } else {
                otherChars++;
            }
        }
        
        int estimatedTokens = (int) (chineseChars / 1.5 + otherChars / 4.0);
        
        log.debug("Estimated tokens for text length {}: {} tokens", text.length(), estimatedTokens);
        return estimatedTokens;
    }
}
