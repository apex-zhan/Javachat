package com.abin.mallchat.ai.vector.service.impl;

import com.abin.mallchat.ai.vector.service.EmbeddingService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * BGE Embedding 服务实现（基于 Ollama + LangChain4j）
 *
 * 推荐方案：通过 Ollama 本地部署 bge-large-zh-v1.5
 *
 * 部署命令：
 * ollama pull bge-large-zh-v1.5
 * 或
 * ollama pull shaw/dmeta-embedding-zh  (替代方案)
 *
 * 特性：
 * - 输出维度：1024
 * - 针对中文优化
 * - 支持批量生成
 *
 * 配置示例：
 * embedding:
 *   provider: bge
 * ollama:
 *   base-url: http://localhost:11434
 *   embedding-model: bge-large-zh-v1.5
 *
 * @author abin
 */
@Slf4j
@Service
@Profile("!mock")
@ConditionalOnProperty(name = "embedding.provider", havingValue = "bge", matchIfMissing = true)
public class OllamaBgeEmbeddingService implements EmbeddingService {

    @Value("${ollama.base-url:http://localhost:11434}")
    private String baseUrl;

    @Value("${ollama.embedding-model:bge-large-zh-v1.5}")
    private String modelName;

    @Value("${ollama.timeout:60s}")
    private Duration timeout;

    @Value("${ollama.max-retries:3}")
    private Integer maxRetries;

    private EmbeddingModel embeddingModel;

    /**
     * 初始化 BGE Embedding Model
     */
    @PostConstruct
    public void init() {
        log.info("Initializing BGE Embedding Model via Ollama: {} at {}", modelName, baseUrl);
        log.info("Note: BGE output dimension is 1024");

        try {
            this.embeddingModel = OllamaEmbeddingModel.builder()
                    .baseUrl(baseUrl)
                    .modelName(modelName)
                    .timeout(timeout)
                    .maxRetries(maxRetries)
                    .build();

            log.info("BGE Embedding Model initialized successfully");

        } catch (Exception e) {
            log.error("Failed to initialize BGE Embedding model", e);
            throw new RuntimeException("Failed to initialize BGE Embedding model", e);
        }
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
            log.debug("Generating BGE embedding for text of length: {}", text.length());

            Response<Embedding> response = embeddingModel.embed(text);
            float[] vector = response.content().vector();

            log.debug("Generated BGE embedding with dimension: {}", vector.length);
            return vector;

        } catch (Exception e) {
            log.error("Failed to generate BGE embedding for text", e);
            throw new RuntimeException("Failed to generate BGE embedding", e);
        }
    }

    /**
     * 批量生成文本向量
     */
    @Override
    public List<float[]> generateEmbeddings(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            log.warn("Empty text list provided for embedding generation");
            throw new IllegalArgumentException("Text list cannot be empty");
        }

        log.info("Generating BGE embeddings for {} texts (batch mode)", texts.size());

        try {
            List<TextSegment> segments = texts.stream()
                    .map(TextSegment::from)
                    .collect(Collectors.toList());

            Response<List<Embedding>> response = embeddingModel.embedAll(segments);

            List<float[]> embeddings = response.content().stream()
                    .map(Embedding::vector)
                    .collect(Collectors.toList());

            log.info("Successfully generated {} BGE embeddings in batch", embeddings.size());
            return embeddings;

        } catch (Exception e) {
            log.error("Failed to generate BGE embeddings for text list in batch", e);
            throw new RuntimeException("Failed to generate BGE embeddings", e);
        }
    }
}