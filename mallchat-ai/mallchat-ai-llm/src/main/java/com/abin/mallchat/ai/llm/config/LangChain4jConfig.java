package com.abin.mallchat.ai.llm.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * LangChain4j 配置类
 * 配置 OpenAI Chat Model、Streaming Chat Model 和 Tokenizer
 * 
 * @author abin
 */
@Slf4j
@Configuration
public class LangChain4jConfig {
    
    @Value("${langchain4j.openai.api-key}")
    private String apiKey;
    
    @Value("${langchain4j.openai.base-url:https://api.openai.com/v1}")
    private String baseUrl;
    
    @Value("${langchain4j.openai.chat-model.model-name:gpt-3.5-turbo}")
    private String modelName;
    
    @Value("${langchain4j.openai.chat-model.temperature:0.7}")
    private Double temperature;
    
    @Value("${langchain4j.openai.chat-model.max-tokens:2000}")
    private Integer maxTokens;
    
    @Value("${langchain4j.openai.timeout:60s}")
    private Duration timeout;
    
    @Value("${langchain4j.openai.max-retries:3}")
    private Integer maxRetries;
    
    @Value("${langchain4j.openai.log-requests:true}")
    private Boolean logRequests;
    
    @Value("${langchain4j.openai.log-responses:false}")
    private Boolean logResponses;
    
    /**
     * 配置同步 Chat Language Model
     * 用于非流式的 LLM 调用
     */
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        log.info("Initializing ChatLanguageModel with model: {}", modelName);
        
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(timeout)
                .maxRetries(maxRetries)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
    }
    
    /**
     * 配置流式 Chat Language Model
     * 用于流式输出的 LLM 调用
     */
    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel() {
        log.info("Initializing StreamingChatLanguageModel with model: {}", modelName);
        
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(timeout)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
    }
    
    /**
     * 配置 OpenAI Tokenizer
     * 用于估算文本的 token 数量
     */
    @Bean
    public OpenAiTokenizer openAiTokenizer() {
        log.info("Initializing OpenAiTokenizer for model: {}", modelName);
        return new OpenAiTokenizer(modelName);
    }
}
