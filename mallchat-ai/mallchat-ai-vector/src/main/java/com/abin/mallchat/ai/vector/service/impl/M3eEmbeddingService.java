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
 * M3E Embedding 服务实现（基于 Ollama + LangChain4j）
 *
 * 备选方案：通过 Ollama 本地部署 m3e-base
 *
 * 部署命令：
 * ollama pull m3e
 * ollama pull shaw/dmeta-embedding-zh  (替代方案，如果 Ollama 官方没有 m3e)
 *
 * 注意：Ollama 官方可能没有 m3e-base 模型，可以使用以下替代方案：
 * 1. 通过 Ollama Modelfile 自定义导入：ollama create m3e -f Modelfile
 * 2. 使用 shaw/dmeta-embedding-zh（中文 Embedding 替代）
 * 3. 通过 Xinference 部署 m3e-base
 *
 * 配置示例：
 * embedding:
 *   provider: m3e
 * ollama:
 *   base-url: http://localhost:11434
 *   embedding-model: m3e
 *
 * @author abin
 */
@Slf4j
@Service
@Profile("!mock")
@ConditionalOnProperty(name = "embedding.provider", havingValue = "m3e")
public class M3eEmbeddingService implements EmbeddingService {

    @Value("${ollama.base-url:http://localhost:11434}")
    private String baseUrl;

    @Value("${ollama.embedding-model:m3e}")
    private String modelName;

    @Value("${ollama.timeout:60s}")
    private Duration timeout;

    @Value("${ollama.max-retries:3}")
    private Integer maxRetries;

    private EmbeddingModel embeddingModel;

    /**
     * 初始化 M3E Embedding Model
     */
    @PostConstruct
    public void init() {
        log.info("Initializing M3E Embedding Model via Ollama: {} at {}", modelName, baseUrl);
        log.info("Note: M3E output dimension is 768 (bge-large-zh-v1.5 is 1024)");

        try {
            this.embeddingModel = OllamaEmbeddingModel.builder()
                    .baseUrl(baseUrl)
                    .modelName(modelName)
                    .timeout(timeout)
                    .maxRetries(maxRetries)
                    .build();

            log.info("M3E Embedding Model initialized successfully");

        } catch (Exception e) {
            log.error("Failed to initialize M3E Embedding model", e);
            throw new RuntimeException("Failed to initialize M3E Embedding model", e);
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
            log.debug("Generating M3E embedding for text of length: {}", text.length());

            Response<Embedding> response = embeddingModel.embed(text);
            float[] vector = response.content().vector();

            log.debug("Generated M3E embedding with dimension: {}", vector.length);
            return vector;

        } catch (Exception e) {
            log.error("Failed to generate M3E embedding for text", e);
            throw new RuntimeException("Failed to generate M3E embedding", e);
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

        log.info("Generating M3E embeddings for {} texts (batch mode)", texts.size());

        try {
            List<TextSegment> segments = texts.stream()
                    .map(TextSegment::from)
                    .collect(Collectors.toList());

            Response<List<Embedding>> response = embeddingModel.embedAll(segments);

            List<float[]> embeddings = response.content().stream()
                    .map(Embedding::vector)
                    .collect(Collectors.toList());

            log.info("Successfully generated {} M3E embeddings in batch", embeddings.size());
            return embeddings;

        } catch (Exception e) {
            log.error("Failed to generate M3E embeddings for text list in batch", e);
            throw new RuntimeException("Failed to generate M3E embeddings", e);
        }
    }
}