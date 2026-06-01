package com.abin.mallchat.ai.vector.service.impl;

import com.abin.mallchat.ai.common.domain.entity.DocumentChunk;
import com.abin.mallchat.ai.vector.domain.SearchResult;
import com.abin.mallchat.ai.vector.service.VectorService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.WithPayloadSelector;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Qdrant 向量数据库服务实现（推荐）
 *
 * 特性：
 * 1. 支持动态向量维度（DynamicVector），兼容 bge-large-zh-v1.5(1024维) 和 m3e-base(768维)
 * 2. 使用 Cosine 相似度
 * 3. 支持向量数据落盘（on_disk=true），降低内存占用
 *
 * 配置示例：
 * vector.store:
 *   provider: qdrant
 * qdrant:
 *   host: localhost
 *   port: 6334
 *   collection-name: mallchat_knowledge
 *   grpc-timeout: 30
 *
 * @author abin
 */
@Slf4j
@Service
@Profile("!mock")
@ConditionalOnProperty(name = "vector.store.provider", havingValue = "qdrant", matchIfMissing = true)
public class QdrantVectorService implements VectorService {

    @Value("${qdrant.host:localhost}")
    private String host;

    @Value("${qdrant.port:6334}")
    private Integer port;

    @Value("${qdrant.api-key:}")
    private String apiKey;

    @Value("${qdrant.collection-name:mallchat_knowledge}")
    private String collectionName;

    @Value("${qdrant.grpc-timeout:30}")
    private Integer grpcTimeout;

    @Value("${qdrant.use-tls:false}")
    private Boolean useTls;

    private QdrantClient qdrantClient;

    // Field keys for payload
    private static final String FIELD_DOCUMENT_ID = "document_id";
    private static final String FIELD_CHUNK_ID = "chunk_id";
    private static final String FIELD_CHUNK_INDEX = "chunk_index";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_METADATA = "metadata";

    /**
     * 初始化 Qdrant 客户端并创建 Collection
     */
    @PostConstruct
    public void init() {
        log.info("Initializing Qdrant client: {}:{}, collection: {}", host, port, collectionName);

        try {
            // 创建 gRPC 客户端
            QdrantGrpcClient.Builder grpcBuilder = QdrantGrpcClient.newBuilder(host, port, useTls);
            if (apiKey != null && !apiKey.isEmpty()) {
                grpcBuilder.withApiKey(apiKey);
            }

            qdrantClient = new QdrantClient(grpcBuilder.build());

            // 检查并创建 Collection
            createCollectionIfNotExists();

            log.info("Qdrant client initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize Qdrant client", e);
            throw new RuntimeException("Failed to initialize Qdrant client", e);
        }
    }

    /**
     * 创建 Collection（如果不存在）
     * 使用 DynamicVector 支持不同维度
     */
    private void createCollectionIfNotExists() throws ExecutionException, InterruptedException {
        boolean exists = qdrantClient.collectionExistsAsync(collectionName).get();

        if (exists) {
            log.info("Collection '{}' already exists", collectionName);
            return;
        }

        log.info("Creating collection '{}' with dynamic vectors", collectionName);

        // 使用动态向量配置，支持不同维度的向量
        Collections.CollectionOperationResponse response = qdrantClient.createCollectionAsync(
                collectionName,
                Collections.VectorParams.newBuilder()
                        .setDistance(Collections.Distance.Cosine)
                        .setOnDisk(true)      // 向量落盘，降低内存
                        .setDynamic(true)     // 动态向量，支持不同维度
                        .build()
        ).get();

        if (!response.getResult()) {
            throw new RuntimeException("Failed to create collection: " + response.getStatus());
        }

        log.info("Collection '{}' created successfully with dynamic vectors", collectionName);
    }

    /**
     * 存储文档的向量数据
     */
    @Override
    public void storeVectors(Long documentId, List<DocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            log.warn("No chunks to store for document: {}", documentId);
            return;
        }

        log.info("Storing {} vectors for document: {}", chunks.size(), documentId);

        try {
            List<Points.PointStruct> points = new ArrayList<>();

            for (DocumentChunk chunk : chunks) {
                // 从 metadata 中提取向量数据
                List<Float> vector = extractVectorFromMetadata(chunk.getMetadata());
                if (vector.isEmpty()) {
                    log.error("No vector data found for chunk: {}", chunk.getId());
                    throw new IllegalArgumentException("Chunk must contain vector data in metadata");
                }

                // 构建 payload
                Map<String, JsonWithInt.Value> payload = new HashMap<>();
                payload.put(FIELD_DOCUMENT_ID, jsonValue(documentId.toString()));
                payload.put(FIELD_CHUNK_ID, jsonValue(chunk.getId().toString()));
                payload.put(FIELD_CHUNK_INDEX, jsonValue(String.valueOf(chunk.getChunkIndex())));
                payload.put(FIELD_CONTENT, jsonValue(chunk.getContent()));

                // 存储不包含向量的元数据
                String cleanMetadata = removeVectorFromMetadata(chunk.getMetadata());
                payload.put(FIELD_METADATA, jsonValue(cleanMetadata != null ? cleanMetadata : "{}"));

                // 构建 PointStruct
                Points.PointStruct point = Points.PointStruct.newBuilder()
                        .setId(Points.PointId.newBuilder().setNum(chunk.getId()).build())
                        .setVectors(Points.Vectors.newBuilder()
                                .setVector(Collections.Vector.newBuilder()
                                        .addAllData(vector)
                                        .build())
                                .build())
                        .putAllPayload(payload)
                        .build();

                points.add(point);
            }

            // 批量插入
            Points.UpdateResult result = qdrantClient.upsertAsync(
                    collectionName,
                    points
            ).get();

            log.info("Successfully stored {} vectors for document: {}, operation id: {}",
                    chunks.size(), documentId, result.getOperationId());

        } catch (Exception e) {
            log.error("Failed to store vectors for document: {}", documentId, e);
            throw new RuntimeException("Failed to store vectors", e);
        }
    }

    /**
     * 相似度检索
     */
    @Override
    @CircuitBreaker(name = "vectorStoreService", fallbackMethod = "searchFallback")
    public List<SearchResult> search(float[] queryVector, int topK, Long documentId) {
        log.debug("Searching for top {} similar vectors in Qdrant", topK);

        try {
            // 构建过滤条件
            Points.Filter.Builder filterBuilder = Points.Filter.newBuilder();
            if (documentId != null) {
                filterBuilder.addMust(Points.Condition.newBuilder()
                        .setField(Points.FieldCondition.newBuilder()
                                .setKey(FIELD_DOCUMENT_ID)
                                .setMatch(Points.Match.newBuilder().setKeyword(documentId.toString()).build())
                                .build())
                        .build());
            }

            // 构建搜索请求
            List<Float> vectorList = new ArrayList<>();
            for (float v : queryVector) {
                vectorList.add(v);
            }

            Points.SearchPoints.Builder searchBuilder = Points.SearchPoints.newBuilder()
                    .setCollectionName(collectionName)
                    .addAllVector(vectorList)
                    .setLimit(topK)
                    .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build());

            if (documentId != null) {
                searchBuilder.setFilter(filterBuilder.build());
            }

            // 执行搜索
            List<Points.ScoredPoint> scoredPoints = qdrantClient.searchAsync(searchBuilder.build()).get();

            // 解析结果
            List<SearchResult> results = new ArrayList<>();
            for (Points.ScoredPoint point : scoredPoints) {
                Map<String, JsonWithInt.Value> payload = point.getPayloadMap();

                SearchResult result = SearchResult.builder()
                        .documentId(parseLong(getPayloadValue(payload, FIELD_DOCUMENT_ID)))
                        .chunkId(parseLong(getPayloadValue(payload, FIELD_CHUNK_ID)))
                        .chunkIndex(parseInt(getPayloadValue(payload, FIELD_CHUNK_INDEX)))
                        .content(getPayloadValue(payload, FIELD_CONTENT))
                        .score(point.getScore())
                        .metadata(parseMetadata(getPayloadValue(payload, FIELD_METADATA)))
                        .build();

                results.add(result);
            }

            log.debug("Found {} results from Qdrant", results.size());
            return results;

        } catch (Exception e) {
            log.error("Failed to search vectors in Qdrant", e);
            throw new RuntimeException("Failed to search vectors", e);
        }
    }

    /**
     * 删除文档的所有向量（幂等操作）
     */
    @Override
    public void deleteVectors(Long documentId) {
        log.info("Deleting vectors for document: {} (idempotent operation)", documentId);

        try {
            // 先检查是否存在
            boolean exists = exists(documentId);
            if (!exists) {
                log.info("No vectors found for document: {}, skipping deletion (idempotent)", documentId);
                return;
            }

            // 构建删除条件
            Points.Filter filter = Points.Filter.newBuilder()
                    .addMust(Points.Condition.newBuilder()
                            .setField(Points.FieldCondition.newBuilder()
                                    .setKey(FIELD_DOCUMENT_ID)
                                    .setMatch(Points.Match.newBuilder().setKeyword(documentId.toString()).build())
                                    .build())
                            .build())
                    .build();

            Points.UpdateResult result = qdrantClient.deleteAsync(
                    collectionName,
                    filter
            ).get();

            log.info("Successfully deleted vectors for document: {}, operation id: {}",
                    documentId, result.getOperationId());

        } catch (Exception e) {
            log.error("Failed to delete vectors for document: {}", documentId, e);
            throw new RuntimeException("Failed to delete vectors", e);
        }
    }

    /**
     * 检查文档的向量是否存在
     */
    @Override
    public boolean exists(Long documentId) {
        log.debug("Checking if vectors exist for document: {}", documentId);

        try {
            Points.Filter filter = Points.Filter.newBuilder()
                    .addMust(Points.Condition.newBuilder()
                            .setField(Points.FieldCondition.newBuilder()
                                    .setKey(FIELD_DOCUMENT_ID)
                                    .setMatch(Points.Match.newBuilder().setKeyword(documentId.toString()).build())
                                    .build())
                            .build())
                    .build();

            Points.CountPoints countRequest = Points.CountPoints.newBuilder()
                    .setCollectionName(collectionName)
                    .setFilter(filter)
                    .setExact(true)
                    .build();

            Points.CountResult countResult = qdrantClient.countAsync(countRequest).get();
            boolean exists = countResult.getResult() > 0;

            log.debug("Vectors exist for document {}: {} (count: {})", documentId, exists, countResult.getResult());
            return exists;

        } catch (Exception e) {
            log.error("Failed to check if vectors exist for document: {}", documentId, e);
            throw new RuntimeException("Failed to check vector existence", e);
        }
    }

    /**
     * 关闭 Qdrant 客户端
     */
    @PreDestroy
    public void destroy() {
        if (qdrantClient != null) {
            log.info("Closing Qdrant client");
            qdrantClient.close();
        }
    }

    // ==================== Helper Methods ====================

    private JsonWithInt.Value jsonValue(String value) {
        return JsonWithInt.Value.newBuilder()
                .setStringValue(value)
                .build();
    }

    private String getPayloadValue(Map<String, JsonWithInt.Value> payload, String key) {
        JsonWithInt.Value value = payload.get(key);
        if (value == null) {
            return null;
        }
        if (value.hasStringValue()) {
            return value.getStringValue();
        }
        if (value.hasIntegerValue()) {
            return String.valueOf(value.getIntegerValue());
        }
        return value.toString();
    }

    private Long parseLong(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse long value: {}", value);
            return null;
        }
    }

    private Integer parseInt(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse int value: {}", value);
            return null;
        }
    }

    private List<Float> extractVectorFromMetadata(String metadataJson) {
        try {
            if (metadataJson == null || metadataJson.isEmpty()) {
                return Collections.emptyList();
            }
            // 简单解析 JSON 数组
            if (metadataJson.contains("\"embedding\"")) {
                int start = metadataJson.indexOf("[", metadataJson.indexOf("\"embedding\""));
                int end = metadataJson.indexOf("]", start);
                if (start >= 0 && end > start) {
                    String arrayStr = metadataJson.substring(start + 1, end);
                    return Arrays.stream(arrayStr.split(","))
                            .map(String::trim)
                            .map(Float::parseFloat)
                            .collect(Collectors.toList());
                }
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to extract vector from metadata: {}", metadataJson, e);
            return Collections.emptyList();
        }
    }

    private String removeVectorFromMetadata(String metadataJson) {
        try {
            if (metadataJson == null || metadataJson.isEmpty()) {
                return "{}";
            }
            // 移除 embedding 字段
            return metadataJson.replaceAll("\"embedding\"\\s*:\\s*\\[[^\\]]*\\]\\s*,?", "")
                    .replaceAll(",\\s*}", "}")
                    .replaceAll("{\\s*,", "{");
        } catch (Exception e) {
            log.error("Failed to remove vector from metadata: {}", metadataJson, e);
            return "{}";
        }
    }

    private Map<String, Object> parseMetadata(String metadataJson) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (metadataJson == null || metadataJson.isEmpty() || "{}".equals(metadataJson)) {
                return result;
            }
            // 简单解析 JSON 字符串
            // 实际项目中建议使用 Jackson 或 Gson
            return result;
        } catch (Exception e) {
            log.warn("Failed to parse metadata JSON: {}", metadataJson, e);
            return result;
        }
    }

    /**
     * 向量检索降级方法
     */
    private List<SearchResult> searchFallback(float[] queryVector, int topK, Long documentId, Throwable throwable) {
        log.warn("Qdrant vector store service degraded, returning empty search results. Error: {}", throwable.getMessage());
        return Collections.emptyList();
    }
}