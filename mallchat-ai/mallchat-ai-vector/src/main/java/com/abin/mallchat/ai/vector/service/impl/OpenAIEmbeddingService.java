package com.abin.mallchat.ai.vector.service.impl;

import com.abin.mallchat.ai.vector.service.EmbeddingService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * OpenAI Embedding 服务实现（基于 LangChain4j）
 * 使用 OpenAI 的 text-embedding-ada-002 模型生成向量
 * 
 * @author abin
 */
@Slf4j
@Service
public class OpenAIEmbeddingService implements EmbeddingService {
    
    @Value("${langchain4j.openai.api-key}")
    private String apiKey;
    
    @Value("${langchain4j.openai.base-url:https://api.openai.com/v1}")
    private String baseUrl;
    
    @Value("${langchain4j.openai.embedding-model.model-name:text-embedding-ada-002}")
    private String modelName;
    
    @Value("${langchain4j.openai.timeout:60s}")
    private Duration timeout;
    
    @Value("${langchain4j.openai.max-retries:3}")
    private Integer maxRetries;
    
    private EmbeddingModel embeddingModel;
    
    /**
     * 初始化 Embedding Model
     */
    @PostConstruct
    public void init() {
        log.info("Initializing OpenAI Embedding Model: {}", modelName);
        
        this.embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(timeout)
                .maxRetries(maxRetries)
                .logRequests(true)
                .logResponses(false)
                .build();
        
        log.info("OpenAI Embedding Model initialized successfully");
    }
    
    /**
     * 生成单个文本的向量
     */
    @Override
    public float[] generateEmbedding(String text) {
        if (text == null || text.trim().isEmpty()) {
            log.warn("Empty text provided for embedding generation");
            throw new IllegalArgumentException("Text cannot be empty");
        }
        
        try {
            log.debug("Generating embedding for text of length: {}", text.length());
            
            Response<Embedding> response = embeddingModel.embed(text);
            float[] vector = response.content().vector();
            
            log.debug("Generated embedding with dimension: {}", vector.length);
            return vector;
            
        } catch (Exception e) {
            log.error("Failed to generate embedding for text", e);
            throw new RuntimeException("Failed to generate embedding", e);
        }
    }
    
    /**
     * 批量生成文本向量（优化版本）
     * 使用 LangChain4j 的批量 API，一次请求生成多个向量
     * 相比循环调用单个方法，可以显著提升性能和降低API调用次数
     */
    @Override
    public List<float[]> generateEmbeddings(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            log.warn("Empty text list provided for embedding generation");
            throw new IllegalArgumentException("Text list cannot be empty");
        }
        
        log.info("Generating embeddings for {} texts (batch mode)", texts.size());
        
        try {
            // 将 String 转换为 TextSegment
            List<TextSegment> segments = texts.stream()
                    .map(TextSegment::from)
                    .collect(Collectors.toList());
            
            // 使用 LangChain4j 的批量 embedAll 方法
            // 这会在一次 API 调用中生成所有向量，大幅提升性能
            Response<List<Embedding>> response = embeddingModel.embedAll(segments);
            
            List<float[]> embeddings = response.content().stream()
                    .map(Embedding::vector)
                    .collect(Collectors.toList());
            
            log.info("Successfully generated {} embeddings in batch", embeddings.size());
            return embeddings;
            
        } catch (Exception e) {
            log.error("Failed to generate embeddings for text list in batch", e);
            throw new RuntimeException("Failed to generate embeddings", e);
        }
    }
}
