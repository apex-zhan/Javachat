package com.abin.mallchat.ai.llm.service.impl;

import com.abin.mallchat.ai.llm.domain.LLMOptions;
import com.abin.mallchat.ai.llm.exception.LLMApiException;
import com.abin.mallchat.ai.llm.service.LLMService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.output.Response;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
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
 * Qwen2.5-14B LLM 服务实现（基于 Ollama + LangChain4j）
 *
 * 推荐方案：通过 Ollama 本地部署 Qwen2.5-14B
 *
 * 部署命令：
 * ollama pull qwen2.5:14b
 * ollama run qwen2.5:14b
 *
 * 配置示例：
 * langchain4j:
 *   llm:
 *     provider: qwen
 *   ollama:
 *     base-url: http://localhost:11434
 *     model-name: qwen2.5:14b
 *     temperature: 0.7
 *     timeout: 120s
 *
 * @author abin
 */
@Slf4j
@Service
@Profile("!mock")
@ConditionalOnProperty(name = "langchain4j.llm.provider", havingValue = "qwen")
public class QwenLLMService implements LLMService {

    @Value("${ollama.base-url:http://localhost:11434}")
    private String baseUrl;

    @Value("${ollama.model-name:qwen2.5:14b}")
    private String modelName;

    @Value("${ollama.temperature:0.7}")
    private Double temperature;

    @Value("${ollama.timeout:120s}")
    private Duration timeout;

    private ChatLanguageModel chatModel;
    private StreamingChatLanguageModel streamingChatModel;

    /**
     * 初始化 Qwen 模型
     */
    @PostConstruct
    public void init() {
        log.info("Initializing Qwen Model via Ollama: {} at {}", modelName, baseUrl);

        try {
            // 构建同步模型
            this.chatModel = OllamaChatModel.builder()
                    .baseUrl(baseUrl)
                    .modelName(modelName)
                    .temperature(temperature)
                    .timeout(timeout)
                    .build();

            // 构建流式模型
            this.streamingChatModel = OllamaStreamingChatModel.builder()
                    .baseUrl(baseUrl)
                    .modelName(modelName)
                    .temperature(temperature)
                    .timeout(timeout)
                    .build();

            log.info("Qwen Model initialized successfully");

        } catch (Exception e) {
            log.error("Failed to initialize Qwen model", e);
            throw new RuntimeException("Failed to initialize Qwen model", e);
        }
    }

    @Override
    @CircuitBreaker(name = "llmService", fallbackMethod = "streamChatFallback")
    @Retryable(
            value = {LLMApiException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public Flux<String> streamChat(String prompt, LLMOptions options) {
        log.info("Qwen stream chat called with prompt length: {}", prompt.length());

        if (streamingChatModel == null) {
            log.error("Qwen streaming model not initialized");
            return Flux.error(new LLMApiException("Qwen streaming model not initialized"));
        }

        return Flux.create(sink -> {
            try {
                streamingChatModel.generate(prompt, new StreamingResponseHandler<AiMessage>() {
                    @Override
                    public void onNext(String token) {
                        if (token != null && !token.isEmpty()) {
                            sink.next(token);
                        }
                    }

                    @Override
                    public void onComplete(Response<AiMessage> response) {
                        log.info("Qwen stream chat completed");
                        sink.complete();
                    }

                    @Override
                    public void onError(Throwable error) {
                        log.error("Qwen streaming error", error);
                        sink.error(new LLMApiException("Qwen API call failed", error));
                    }
                });
            } catch (Exception e) {
                log.error("Qwen stream chat failed", e);
                sink.error(new LLMApiException("Qwen stream chat failed", e));
            }
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    @Override
    @CircuitBreaker(name = "llmService", fallbackMethod = "chatFallback")
    @Retryable(
            value = {LLMApiException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public String chat(String prompt, LLMOptions options) {
        log.info("Qwen chat called with prompt length: {}", prompt.length());

        if (chatModel == null) {
            log.error("Qwen model not initialized");
            throw new LLMApiException("Qwen model not initialized");
        }

        try {
            String response = chatModel.generate(prompt);
            log.debug("Qwen response length: {}", response.length());
            return response;

        } catch (Exception e) {
            log.error("Qwen chat failed", e);
            throw new LLMApiException("Qwen API call failed", e);
        }
    }

    @Override
    @CircuitBreaker(name = "llmService", fallbackMethod = "streamChatMessagesFallback")
    @Retryable(
            value = {LLMApiException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public Flux<String> streamChat(List<ChatMessage> messages, LLMOptions options) {
        log.info("Qwen stream chat with messages called, count: {}", messages.size());

        if (streamingChatModel == null) {
            log.error("Qwen streaming model not initialized");
            return Flux.error(new LLMApiException("Qwen streaming model not initialized"));
        }

        return Flux.create(sink -> {
            try {
                streamingChatModel.generate(messages, new StreamingResponseHandler<AiMessage>() {
                    @Override
                    public void onNext(String token) {
                        if (token != null && !token.isEmpty()) {
                            sink.next(token);
                        }
                    }

                    @Override
                    public void onComplete(Response<AiMessage> response) {
                        log.info("Qwen stream chat with messages completed");
                        sink.complete();
                    }

                    @Override
                    public void onError(Throwable error) {
                        log.error("Qwen streaming error", error);
                        sink.error(new LLMApiException("Qwen API call failed", error));
                    }
                });
            } catch (Exception e) {
                log.error("Qwen stream chat with messages failed", e);
                sink.error(new LLMApiException("Qwen stream chat failed", e));
            }
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    @Override
    @CircuitBreaker(name = "llmService", fallbackMethod = "chatMessagesFallback")
    @Retryable(
            value = {LLMApiException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public String chat(List<ChatMessage> messages, LLMOptions options) {
        log.info("Qwen chat with messages called, count: {}", messages.size());

        if (chatModel == null) {
            log.error("Qwen model not initialized");
            throw new LLMApiException("Qwen model not initialized");
        }

        try {
            Response<AiMessage> response = chatModel.generate(messages);
            String responseText = response.content().text();
            log.debug("Qwen response length: {}", responseText.length());
            return responseText;

        } catch (Exception e) {
            log.error("Qwen chat with messages failed", e);
            throw new LLMApiException("Qwen API call failed", e);
        }
    }

    @Override
    public int countTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        // Qwen2.5 使用 QwenTokenizer，但 Ollama 不直接提供 token 计数
        // 使用近似估算：中文约 1.5 字符/token，英文约 4 字符/token
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

    // ==================== Fallback Methods ====================

    private Flux<String> streamChatFallback(String prompt, LLMOptions options, Throwable throwable) {
        log.warn("Qwen LLM service degraded, using fallback for stream chat. Error: {}", throwable.getMessage());
        return Flux.just("抱歉，AI服务暂时不可用，请稍后再试。");
    }

    private String chatFallback(String prompt, LLMOptions options, Throwable throwable) {
        log.warn("Qwen LLM service degraded, using fallback for chat. Error: {}", throwable.getMessage());
        return "抱歉，AI服务暂时不可用，请稍后再试。";
    }

    private Flux<String> streamChatMessagesFallback(List<ChatMessage> messages, LLMOptions options, Throwable throwable) {
        log.warn("Qwen LLM service degraded, using fallback for stream chat with messages. Error: {}", throwable.getMessage());
        return Flux.just("抱歉，AI服务暂时不可用，请稍后再试。");
    }

    private String chatMessagesFallback(List<ChatMessage> messages, LLMOptions options, Throwable throwable) {
        log.warn("Qwen LLM service degraded, using fallback for chat with messages. Error: {}", throwable.getMessage());
        return "抱歉，AI服务暂时不可用，请稍后再试。";
    }
}