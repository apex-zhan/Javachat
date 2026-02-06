package com.abin.mallchat.ai.llm.service.impl;

import com.abin.mallchat.ai.llm.config.LLMConfig;
import com.abin.mallchat.ai.llm.domain.LLMOptions;
import com.abin.mallchat.ai.llm.exception.LLMApiException;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.StreamingResponseHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * OpenAI LLM Service 单元测试（基于 LangChain4j）
 * 
 * @author abin
 */
@ExtendWith(MockitoExtension.class)
class OpenAILLMServiceTest {
    
    @Mock
    private ChatLanguageModel chatLanguageModel;
    
    @Mock
    private StreamingChatLanguageModel streamingChatLanguageModel;
    
    @Mock
    private OpenAiTokenizer tokenizer;
    
    @Mock
    private LLMConfig llmConfig;
    
    @InjectMocks
    private OpenAILLMService llmService;
    
    @BeforeEach
    void setUp() {
        when(llmConfig.getModel()).thenReturn("gpt-3.5-turbo");
    }
    
    /**
     * 测试流式调用返回 Flux 类型
     */
    @Test
    void testStreamChatReturnsFlux() {
        // Given
        String prompt = "Hello, AI!";
        LLMOptions options = LLMOptions.defaultOptions();
        
        // Mock streaming response - 使用 doAnswer 模拟流式回调
        doAnswer(invocation -> {
            StreamingResponseHandler<AiMessage> handler = invocation.getArgument(1);
            handler.onNext("Hello");
            handler.onNext(" there");
            handler.onNext("!");
            handler.onComplete(Response.from(AiMessage.from("Hello there!")));
            return null;
        }).when(streamingChatLanguageModel).generate(anyString(), any(StreamingResponseHandler.class));
        
        // When
        Flux<String> result = llmService.streamChat(prompt, options);
        
        // Then - 验证返回 Flux 类型
        assertThat(result).isNotNull();
        
        // 验证流式数据
        StepVerifier.create(result)
                .expectNext("Hello")
                .expectNext(" there")
                .expectNext("!")
                .verifyComplete();
    }
    
    /**
     * 测试流式调用过滤空内容
     */
    @Test
    void testStreamChatFiltersEmptyContent() {
        // Given
        String prompt = "Test prompt";
        LLMOptions options = LLMOptions.defaultOptions();
        
        // Mock response with empty content
        doAnswer(invocation -> {
            StreamingResponseHandler<AiMessage> handler = invocation.getArgument(1);
            handler.onNext("Content");
            handler.onNext("");  // 空内容应该被过滤
            handler.onNext("More");
            handler.onComplete(Response.from(AiMessage.from("ContentMore")));
            return null;
        }).when(streamingChatLanguageModel).generate(anyString(), any(StreamingResponseHandler.class));
        
        // When
        Flux<String> result = llmService.streamChat(prompt, options);
        
        // Then - 空内容应该被过滤
        StepVerifier.create(result)
                .expectNext("Content")
                .expectNext("More")
                .verifyComplete();
    }
    
    /**
     * 测试非流式调用
     */
    @Test
    void testChatReturnsCompleteResponse() {
        // Given
        String prompt = "What is AI?";
        LLMOptions options = LLMOptions.defaultOptions();
        String expectedResponse = "AI stands for Artificial Intelligence.";
        
        when(chatLanguageModel.generate(anyString())).thenReturn(expectedResponse);
        
        // When
        String result = llmService.chat(prompt, options);
        
        // Then
        assertThat(result).isEqualTo(expectedResponse);
    }
    
    /**
     * 测试空响应处理
     */
    @Test
    void testChatThrowsExceptionOnEmptyResponse() {
        // Given
        String prompt = "Test";
        LLMOptions options = LLMOptions.defaultOptions();
        
        when(chatLanguageModel.generate(anyString())).thenReturn("");
        
        // When & Then
        assertThatThrownBy(() -> llmService.chat(prompt, options))
                .isInstanceOf(LLMApiException.class)
                .hasMessageContaining("Empty response");
    }
    
    /**
     * 测试 token 计数准确性 - 纯英文
     */
    @Test
    void testCountTokensForEnglishText() {
        // Given
        String text = "Hello world this is a test";
        when(tokenizer.estimateTokenCountInText(text)).thenReturn(8);
        
        // When
        int tokenCount = llmService.countTokens(text);
        
        // Then
        assertThat(tokenCount).isEqualTo(8);
    }
    
    /**
     * 测试 token 计数准确性 - 纯中文
     */
    @Test
    void testCountTokensForChineseText() {
        // Given
        String text = "你好世界这是测试";
        when(tokenizer.estimateTokenCountInText(text)).thenReturn(12);
        
        // When
        int tokenCount = llmService.countTokens(text);
        
        // Then
        assertThat(tokenCount).isEqualTo(12);
    }
    
    /**
     * 测试 token 计数准确性 - 中英混合
     */
    @Test
    void testCountTokensForMixedText() {
        // Given
        String text = "Hello 世界 this is 测试";
        when(tokenizer.estimateTokenCountInText(text)).thenReturn(10);
        
        // When
        int tokenCount = llmService.countTokens(text);
        
        // Then
        assertThat(tokenCount).isEqualTo(10);
    }
    
    /**
     * 测试空文本 token 计数
     */
    @Test
    void testCountTokensForEmptyText() {
        // Given
        String text = "";
        
        // When
        int tokenCount = llmService.countTokens(text);
        
        // Then
        assertThat(tokenCount).isEqualTo(0);
    }
    
    /**
     * 测试 null 文本 token 计数
     */
    @Test
    void testCountTokensForNullText() {
        // When
        int tokenCount = llmService.countTokens(null);
        
        // Then
        assertThat(tokenCount).isEqualTo(0);
    }
    
    /**
     * 测试错误处理 - 流式调用失败
     */
    @Test
    void testStreamChatHandlesError() {
        // Given
        String prompt = "Test";
        LLMOptions options = LLMOptions.defaultOptions();
        
        doAnswer(invocation -> {
            StreamingResponseHandler<AiMessage> handler = invocation.getArgument(1);
            handler.onError(new RuntimeException("API Error"));
            return null;
        }).when(streamingChatLanguageModel).generate(anyString(), any(StreamingResponseHandler.class));
        
        // When
        Flux<String> result = llmService.streamChat(prompt, options);
        
        // Then
        StepVerifier.create(result)
                .expectError(LLMApiException.class)
                .verify();
    }
    
    /**
     * 测试自定义 LLM 选项
     */
    @Test
    void testChatWithCustomOptions() {
        // Given
        String prompt = "Summarize this";
        LLMOptions options = LLMOptions.builder()
                .temperature(0.3)
                .maxTokens(500)
                .model("gpt-4")
                .build();
        
        String expectedResponse = "Summary content";
        when(chatLanguageModel.generate(anyString())).thenReturn(expectedResponse);
        
        // When
        String result = llmService.chat(prompt, options);
        
        // Then
        assertThat(result).isEqualTo(expectedResponse);
    }
    
    /**
     * 测试 tokenizer 失败时的降级方案
     */
    @Test
    void testCountTokensFallbackOnError() {
        // Given
        String text = "Hello world";
        when(tokenizer.estimateTokenCountInText(text)).thenThrow(new RuntimeException("Tokenizer error"));
        
        // When
        int tokenCount = llmService.countTokens(text);
        
        // Then - 应该使用降级方案，返回大于0的值
        assertThat(tokenCount).isGreaterThan(0);
    }
}
