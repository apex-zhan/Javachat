package com.abin.mallchat.ai.vector.service.impl;

import com.abin.mallchat.ai.common.domain.entity.DocumentChunk;
import com.abin.mallchat.ai.vector.domain.SearchResult;
import com.abin.mallchat.ai.vector.service.VectorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Mock 向量数据库服务实现
 *
 * 用于本地开发/测试环境，无需部署真实向量数据库即可启动项目。
 * 基于内存存储，使用余弦相似度进行检索。
 *
 * 特性：
 * - 内存存储（ConcurrentHashMap），线程安全
 * - 支持余弦相似度检索
 * - 支持按文档ID过滤
 * - 数据在JVM重启后丢失（仅用于测试）
 *
 * 启用方式：spring.profiles.active=mock
 *
 * @author abin
 */
@Slf4j
@Service
@Profile("mock")
public class MockVectorService implements VectorService {

    /**
     * 内存存储：documentId -> List<ChunkVector>
     */
    private final Map<Long, List<ChunkVector>> storage = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("[Mock] MockVectorService initialized (in-memory storage)");
        log.warn("[Mock] 数据仅存储在内存中，JVM重启后丢失！");
    }

    @Override
    public void storeVectors(Long documentId, List<DocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            log.warn("[Mock] No chunks to store for document: {}", documentId);
            return;
        }

        log.info("[Mock] Storing {} vectors for document: {}", chunks.size(), documentId);

        List<ChunkVector> vectors = chunks.stream()
                .map(chunk -> {
                    float[] vector = extractVector(chunk);
                    return ChunkVector.builder()
                            .documentId(documentId)
                            .chunkId(chunk.getId())
                            .chunkIndex(chunk.getChunkIndex())
                            .content(chunk.getContent())
                            .vector(vector)
                            .metadata(chunk.getMetadata())
                            .build();
                })
                .collect(Collectors.toList());

        storage.put(documentId, vectors);
        log.info("[Mock] Successfully stored {} vectors for document: {}", vectors.size(), documentId);
    }

    @Override
    public List<SearchResult> search(float[] queryVector, int topK, Long documentId) {
        log.debug("[Mock] Searching for top {} similar vectors", topK);

        List<ScoredResult> allResults = new ArrayList<>();

        // 遍历所有文档
        for (Map.Entry<Long, List<ChunkVector>> entry : storage.entrySet()) {
            Long docId = entry.getKey();

            // 如果指定了documentId，只搜索该文档
            if (documentId != null && !documentId.equals(docId)) {
                continue;
            }

            for (ChunkVector chunk : entry.getValue()) {
                double similarity = cosineSimilarity(queryVector, chunk.getVector());
                allResults.add(new ScoredResult(chunk, similarity));
            }
        }

        // 按相似度降序排序
        allResults.sort((a, b) -> Double.compare(b.similarity, a.similarity));

        // 取前topK个
        List<SearchResult> results = allResults.stream()
                .limit(topK)
                .map(scored -> SearchResult.builder()
                        .documentId(scored.chunk.getDocumentId())
                        .chunkId(scored.chunk.getChunkId())
                        .chunkIndex(scored.chunk.getChunkIndex())
                        .content(scored.chunk.getContent())
                        .score((float) scored.similarity)
                        .metadata(scored.chunk.getMetadata())
                        .build())
                .collect(Collectors.toList());

        log.debug("[Mock] Found {} results", results.size());
        return results;
    }

    @Override
    public void deleteVectors(Long documentId) {
        log.info("[Mock] Deleting vectors for document: {}", documentId);
        storage.remove(documentId);
        log.info("[Mock] Vectors deleted for document: {}", documentId);
    }

    @Override
    public boolean exists(Long documentId) {
        boolean exists = storage.containsKey(documentId);
        log.debug("[Mock] Vectors exist for document {}: {}", documentId, exists);
        return exists;
    }

    /**
     * 计算余弦相似度
     */
    private double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 从DocumentChunk的metadata中提取向量
     */
    private float[] extractVector(DocumentChunk chunk) {
        String metadata = chunk.getMetadata();
        if (metadata == null || metadata.isEmpty()) {
            return new float[0];
        }

        try {
            // 尝试从metadata JSON中提取embedding数组
            if (metadata.contains("\"embedding\"")) {
                int start = metadata.indexOf("[", metadata.indexOf("\"embedding\""));
                int end = metadata.indexOf("]", start);
                if (start >= 0 && end > start) {
                    String[] parts = metadata.substring(start + 1, end).split(",");
                    float[] vector = new float[parts.length];
                    for (int i = 0; i < parts.length; i++) {
                        vector[i] = Float.parseFloat(parts[i].trim());
                    }
                    return vector;
                }
            }
        } catch (Exception e) {
            log.warn("[Mock] Failed to extract vector from metadata, using empty vector");
        }

        return new float[0];
    }

    // ==================== Inner Classes ====================

    /**
     * 带分数的检索结果
     */
    private static class ScoredResult {
        final ChunkVector chunk;
        final double similarity;

        ScoredResult(ChunkVector chunk, double similarity) {
            this.chunk = chunk;
            this.similarity = similarity;
        }
    }

    /**
     * 向量块
     */
    private static class ChunkVector {
        private Long documentId;
        private Long chunkId;
        private Integer chunkIndex;
        private String content;
        private float[] vector;
        private String metadata;

        static ChunkVector builder() {
            return new ChunkVector();
        }

        ChunkVector documentId(Long documentId) {
            this.documentId = documentId;
            return this;
        }

        ChunkVector chunkId(Long chunkId) {
            this.chunkId = chunkId;
            return this;
        }

        ChunkVector chunkIndex(Integer chunkIndex) {
            this.chunkIndex = chunkIndex;
            return this;
        }

        ChunkVector content(String content) {
            this.content = content;
            return this;
        }

        ChunkVector vector(float[] vector) {
            this.vector = vector;
            return this;
        }

        ChunkVector metadata(String metadata) {
            this.metadata = metadata;
            return this;
        }

        ChunkVector build() {
            return this;
        }

        // Getters
        Long getDocumentId() { return documentId; }
        Long getChunkId() { return chunkId; }
        Integer getChunkIndex() { return chunkIndex; }
        String getContent() { return content; }
        float[] getVector() { return vector; }
        String getMetadata() { return metadata; }
    }
}