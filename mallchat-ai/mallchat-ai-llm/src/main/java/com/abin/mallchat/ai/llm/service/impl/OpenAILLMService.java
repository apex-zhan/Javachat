package com.abin.mallchat.ai.llm.service.impl;

import com.abin.mallchat.ai.llm.config.LLMConfig;
import com.abin.mallchat.ai.llm.domain.LLMOptions;
import com.abin.mallchat.ai.llm.exception.LLMApiException;
import com.abin.mallchat.ai.llm.service.LLMService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.StreamingResponseHandler;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * OpenAI LLM 服务实现（基于 LangChain4j）
 * 支持流式和非流式调用
 * 
 * @author abin
 */
@Slf4j
@Service
public class OpenAILLMService implements LLMService {
    
    @Autowired
    private ChatLanguageModel chatLanguageModel;
    
    @Autowired
    private StreamingChatLanguageModel streamingChatLanguageModel;
    
    @Autowired
    private OpenAiTokenizer tokenizer;
    
    @Autowired
    private LLMConfig llmConfig;
    
    /**
     * 流式调用 LLM
     * 使用 @Retryable 实现自动重试
     * 使用 @CircuitBreaker 实现熔断降级
     */
    @Override
    @CircuitBreaker(name = "llmService", fallbackMethod = "streamChatFallback")
    @Retryable(
            value = {LLMApiException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public Flux<String> streamChat(String prompt, LLMOptions options) {
        log.info("Stream chat request, prompt length: {}", prompt.length());
        
        try {
            // 使用 Flux.create 创建流式响应
            return Flux.create(sink -> {
                streamingChatLanguageModel.generate(
                    prompt,
                    new StreamingResponseHandler<AiMessage>() {
                        @Override
                        public void onNext(String token) {
                            // 每收到一个 token 就发送给客户端
                            if (token != null && !token.isEmpty()) {
                                sink.next(token);
                            }
                        }
                        
                        @Override
                        public void onComplete(Response<AiMessage> response) {
                            // 流式响应完成
                            log.info("Stream chat completed");
                            sink.complete();
                        }
                        
                        @Override
                        public void onError(Throwable error) {
                            // 流式响应出错
                            log.error("Stream chat error", error);
                            sink.error(new LLMApiException("Stream chat failed", error));
                        }
                    }
                );
            });
            
        } catch (Exception e) {
            log.error("Failed to initiate stream chat", e);
            throw new LLMApiException("Failed to initiate stream chat", e);
        }
    }
    
    /**
     * 非流式调用 LLM
     * 使用 @Retryable 实现自动重试
     * 使用 @CircuitBreaker 实现熔断降级
     */
    @Override
    @CircuitBreaker(name = "llmService", fallbackMethod = "chatFallback")
    @Retryable(
            value = {LLMApiException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public String chat(String prompt, LLMOptions options) {
        log.info("Chat request, prompt length: {}", prompt.length());
        
        try {
            // 调用同步 API
            String response = chatLanguageModel.generate(prompt);
            
            if (response != null && !response.isEmpty()) {
                log.info("Chat completed, response length: {}", response.length());
                return response;
            }
            
            throw new LLMApiException("Empty response from LLM");
            
        } catch (Exception e) {
            log.error("Chat request failed", e);
            throw new LLMApiException("Chat request failed", e);
        }
    }
    
    /**
     * 计算 token 数量
     * 使用 OpenAiTokenizer 进行估算
     */
    @Override
    public int countTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        try {
            int tokenCount = tokenizer.estimateTokenCountInText(text);
            log.debug("Token count estimation: {} tokens for {} characters", tokenCount, text.length());
            return tokenCount;
        } catch (Exception e) {
            log.warn("Failed to count tokens, falling back to simple estimation", e);
            // 降级方案：简单估算
            return estimateTokensSimple(text);
        }
    }
    
    /**
     * 简单的 token 估算算法（降级方案）
     * 中文字符：1 字符 ≈ 1.5 token
     * 英文单词：1 单词 ≈ 1.3 token
     */
    private int estimateTokensSimple(String text) {
        int chineseCount = 0;
        int englishWords = 0;
        
        for (char c : text.toCharArray()) {
            if (c >= 0x4E00 && c <= 0x9FA5) {
                chineseCount++;
            }
        }
        
        // 估算英文单词数
        String[] words = text.split("\\s+");
        for (String word : words) {
            if (word.matches("[a-zA-Z]+")) {
                englishWords++;
            }
        }
        
        return (int) (chineseCount * 1.5 + englishWords * 1.3);
    }
    
    /**
     * 流式调用降级方法
     * 当 LLM 服务不可用时返回友好提示
     */
    private Flux<String> streamChatFallback(String prompt, LLMOptions options, Throwable throwable) {
        log.warn("LLM service degraded, using fallback for stream chat. Error: {}", throwable.getMessage());
        return Flux.just("抱歉，AI服务暂时不可用，请稍后再试。");
    }
    
    /**
     * 非流式调用降级方法
     * 当 LLM 服务不可用时返回友好提示
     */
    private String chatFallback(String prompt, LLMOptions options, Throwable throwable) {
        log.warn("LLM service degraded, using fallback for chat. Error: {}", throwable.getMessage());
        return "抱歉，AI服务暂时不可用，请稍后再试。";
    }
}
