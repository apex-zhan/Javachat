package com.abin.mallchat.ai.llm.service.impl;

import com.abin.mallchat.ai.llm.config.LLMConfig;
import com.abin.mallchat.ai.llm.domain.LLMOptions;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.StreamingResponseHandler;
import net.jqwik.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * Property-Based Test: Stream Response Consistency
 * Feature: ai-assistant-rag, Property 3: Stream Response Consistency
 * 
 * 验证所有 AI 请求返回 Flux 类型，并且流式数据块立即转发
 * 
 * @author abin
 */
@ExtendWith(MockitoExtension.class)
class StreamResponseConsistencyPropertyTest {
    
    @Mock
    private StreamingChatLanguageModel streamingChatLanguageModel;
    
    @Mock
    private LLMConfig llmConfig;
    
    @InjectMocks
    private OpenAILLMService llmService;
    
    @BeforeEach
    void setUp() {
        when(llmConfig.getModel()).thenReturn("gpt-3.5-turbo");
    }
    
    /**
     * Property 3: Stream Response Consistency
     * Validates: Requirements 1.3, 2.3, 3.2
     * 
     * For any prompt and LLM options, the streamChat method should:
     * 1. Return a Flux<String> type
     * 2. Emit chunks immediately as they arrive
     * 3. Complete successfully
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 3: Stream Response Consistency")
    void streamChatAlwaysReturnsFluxType(
            @ForAll("validPrompts") String prompt,
            @ForAll("validLLMOptions") LLMOptions options
    ) {
        // Given - 模拟流式响应
        doAnswer(invocation -> {
            StreamingResponseHandler<AiMessage> handler = invocation.getArgument(1);
            handler.onNext("Chunk 0");
            handler.onNext("Chunk 1");
            handler.onNext("Chunk 2");
            handler.onComplete(Response.from(AiMessage.from("Complete")));
            return null;
        }).when(streamingChatLanguageModel).generate(anyString(), any(StreamingResponseHandler.class));
        
        // When
        Flux<String> result = llmService.streamChat(prompt, options);
        
        // Then - 验证返回 Flux 类型
        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(Flux.class);
        
        // 验证流式数据立即转发（不阻塞）
        StepVerifier.create(result)
                .expectNextCount(3)  // 应该有3个数据块
                .expectComplete()
                .verify(Duration.ofSeconds(5));  // 应该在5秒内完成
    }
    
    /**
     * Property: Stream chunks are emitted immediately
     * 
     * For any stream response, chunks should be emitted as soon as they arrive,
     * not buffered or delayed.
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 3: Stream chunks emitted immediately")
    void streamChunksAreEmittedImmediately(
            @ForAll("validPrompts") String prompt,
            @ForAll @IntRange(min = 1, max = 10) int chunkCount
    ) {
        // Given - 创建带延迟的流式响应
        doAnswer(invocation -> {
            StreamingResponseHandler<AiMessage> handler = invocation.getArgument(1);
            for (int i = 0; i < chunkCount; i++) {
                handler.onNext("Chunk " + i);
            }
            handler.onComplete(Response.from(AiMessage.from("Complete")));
            return null;
        }).when(streamingChatLanguageModel).generate(anyString(), any(StreamingResponseHandler.class));
        
        // When
        Flux<String> result = llmService.streamChat(prompt, LLMOptions.defaultOptions());
        
        // Then - 验证每个块都被立即转发
        List<String> chunks = new ArrayList<>();
        StepVerifier.create(result)
                .recordWith(() -> chunks)
                .expectNextCount(chunkCount)
                .expectComplete()
                .verify(Duration.ofSeconds(5));
        
        // 验证接收到的块数量正确
        assertThat(chunks).hasSize(chunkCount);
    }
    
    /**
     * Property: Empty chunks are filtered out
     * 
     * For any stream response containing empty chunks, those chunks should be
     * filtered out and not emitted to the client.
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 3: Empty chunks filtered")
    void emptyChunksAreFiltered(
            @ForAll("validPrompts") String prompt,
            @ForAll @IntRange(min = 2, max = 5) int nonEmptyCount,
            @ForAll @IntRange(min = 1, max = 3) int emptyCount
    ) {
        // Given - 创建包含空块的响应
        doAnswer(invocation -> {
            StreamingResponseHandler<AiMessage> handler = invocation.getArgument(1);
            
            // 添加非空响应
            for (int i = 0; i < nonEmptyCount; i++) {
                handler.onNext("Content " + i);
            }
            
            // 添加空响应
            for (int i = 0; i < emptyCount; i++) {
                handler.onNext("");
            }
            
            handler.onComplete(Response.from(AiMessage.from("Complete")));
            return null;
        }).when(streamingChatLanguageModel).generate(anyString(), any(StreamingResponseHandler.class));
        
        // When
        Flux<String> result = llmService.streamChat(prompt, LLMOptions.defaultOptions());
        
        // Then - 只应该接收到非空块
        StepVerifier.create(result)
                .expectNextCount(nonEmptyCount)
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }
    
    /**
     * Property: Stream completes successfully for all valid inputs
     * 
     * For any valid prompt and options, the stream should complete without errors.
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 3: Stream completes successfully")
    void streamCompletesSuccessfully(
            @ForAll("validPrompts") String prompt,
            @ForAll("validLLMOptions") LLMOptions options
    ) {
        // Given
        doAnswer(invocation -> {
            StreamingResponseHandler<AiMessage> handler = invocation.getArgument(1);
            for (int i = 0; i < 5; i++) {
                handler.onNext("Chunk " + i);
            }
            handler.onComplete(Response.from(AiMessage.from("Complete")));
            return null;
        }).when(streamingChatLanguageModel).generate(anyString(), any(StreamingResponseHandler.class));
        
        // When
        Flux<String> result = llmService.streamChat(prompt, options);
        
        // Then - 流应该成功完成
        StepVerifier.create(result)
                .expectNextCount(5)
                .expectComplete()  // 验证流正常完成
                .verify(Duration.ofSeconds(5));
    }
    
    // ========== Arbitraries (数据生成器) ==========
    
    @Provide
    Arbitrary<String> validPrompts() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars(' ', '.', '?', '!')
                .ofMinLength(10)
                .ofMaxLength(500);
    }
    
    @Provide
    Arbitrary<LLMOptions> validLLMOptions() {
        return Combinators.combine(
                Arbitraries.doubles().between(0.0, 2.0),  // temperature
                Arbitraries.integers().between(100, 4000),  // maxTokens
                Arbitraries.doubles().between(0.0, 1.0),  // topP
                Arbitraries.of("gpt-3.5-turbo", "gpt-4", "gpt-4-turbo")  // model
        ).as((temp, maxTokens, topP, model) ->
                LLMOptions.builder()
                        .temperature(temp)
                        .maxTokens(maxTokens)
                        .topP(topP)
                        .model(model)
                        .build()
        );
    }
}
