package com.abin.mallchat.ai.vector.service.impl;

import com.abin.mallchat.ai.common.domain.entity.DocumentChunk;
import com.abin.mallchat.ai.vector.domain.SearchResult;
import com.abin.mallchat.ai.vector.service.VectorService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.DataType;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.SearchResults;
import io.milvus.param.*;
import io.milvus.param.collection.*;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.QueryParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.QueryResultsWrapper;
import io.milvus.response.SearchResultsWrapper;
import io.milvus.grpc.QueryResults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Milvus 向量数据库服务实现
 * 负责向量的存储、检索和删除操作
 * 
 * @author abin
 */
@Slf4j
@Service
public class MilvusVectorService implements VectorService {
    
    @Value("${milvus.host}")
    private String host;
    
    @Value("${milvus.port}")
    private Integer port;
    
    @Value("${milvus.connect-timeout:10}")
    private Long connectTimeout;
    
    @Value("${milvus.keep-alive-time:55}")
    private Long keepAliveTime;
    
    @Value("${milvus.keep-alive-timeout:20}")
    private Long keepAliveTimeout;
    
    @Value("${milvus.secure:false}")
    private Boolean secure;
    
    @Value("${milvus.username:}")
    private String username;
    
    @Value("${milvus.password:}")
    private String password;
    
    @Value("${milvus.database:default}")
    private String database;
    
    @Value("${milvus.collection.name}")
    private String collectionName;
    
    @Value("${milvus.collection.dimension}")
    private Integer dimension;
    
    @Value("${milvus.collection.index-type}")
    private String indexType;
    
    @Value("${milvus.collection.metric-type}")
    private String metricType;
    
    @Value("${milvus.collection.index-params.nlist:1024}")
    private Integer nlist;
    
    @Value("${milvus.collection.search-params.nprobe:10}")
    private Integer nprobe;
    
    private MilvusServiceClient milvusClient;
    private final Gson gson = new Gson();
    
    // Field names
    private static final String FIELD_ID = "id";
    private static final String FIELD_DOCUMENT_ID = "document_id";
    private static final String FIELD_CHUNK_ID = "chunk_id";
    private static final String FIELD_CHUNK_INDEX = "chunk_index";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_VECTOR = "vector";
    private static final String FIELD_METADATA = "metadata";
    
    /**
     * 初始化 Milvus 客户端并创建 Collection
     */
    @PostConstruct
    public void init() {
        log.info("Initializing Milvus client: {}:{}", host, port);
        
        try {
            // 创建 Milvus 客户端
            ConnectParam.Builder connectBuilder = ConnectParam.newBuilder()
                    .withHost(host)
                    .withPort(port)
                    .withConnectTimeout(connectTimeout, TimeUnit.SECONDS)
                    .withKeepAliveTime(keepAliveTime, TimeUnit.SECONDS)
                    .withKeepAliveTimeout(keepAliveTimeout, TimeUnit.SECONDS)
                    .withSecure(secure)
                    .withDatabaseName(database);
            
            // 如果配置了用户名和密码，添加认证
            if (username != null && !username.isEmpty()) {
                connectBuilder.withAuthorization(username, password);
            }
            
            milvusClient = new MilvusServiceClient(connectBuilder.build());
            log.info("Milvus client connected successfully");
            
            // 创建或验证 Collection
            createCollectionIfNotExists();
            
        } catch (Exception e) {
            log.error("Failed to initialize Milvus client", e);
            throw new RuntimeException("Failed to initialize Milvus client", e);
        }
    }
    
    /**
     * 创建 Collection（如果不存在）
     */
    private void createCollectionIfNotExists() {
        // 检查 Collection 是否存在
        R<Boolean> hasCollectionResp = milvusClient.hasCollection(
                HasCollectionParam.newBuilder()
                        .withCollectionName(collectionName)
                        .build()
        );
        
        if (hasCollectionResp.getData()) {
            log.info("Collection '{}' already exists", collectionName);
            return;
        }
        
        log.info("Creating collection '{}'", collectionName);
        
        // 定义字段
        List<FieldType> fields = Arrays.asList(
                FieldType.newBuilder()
                        .withName(FIELD_ID)
                        .withDataType(DataType.Int64)
                        .withPrimaryKey(true)
                        .withAutoID(true)
                        .build(),
                FieldType.newBuilder()
                        .withName(FIELD_DOCUMENT_ID)
                        .withDataType(DataType.Int64)
                        .build(),
                FieldType.newBuilder()
                        .withName(FIELD_CHUNK_ID)
                        .withDataType(DataType.Int64)
                        .build(),
                FieldType.newBuilder()
                        .withName(FIELD_CHUNK_INDEX)
                        .withDataType(DataType.Int32)
                        .build(),
                FieldType.newBuilder()
                        .withName(FIELD_CONTENT)
                        .withDataType(DataType.VarChar)
                        .withMaxLength(65535)
                        .build(),
                FieldType.newBuilder()
                        .withName(FIELD_VECTOR)
                        .withDataType(DataType.FloatVector)
                        .withDimension(dimension)
                        .build(),
                FieldType.newBuilder()
                        .withName(FIELD_METADATA)
                        .withDataType(DataType.VarChar)
                        .withMaxLength(65535)
                        .build()
        );
        
        // 创建 Collection
        CreateCollectionParam createCollectionParam = CreateCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .withDescription("MallChat Knowledge Vectors")
                .withFieldTypes(fields)
                .build();
        
        R<RpcStatus> createResp = milvusClient.createCollection(createCollectionParam);
        if (createResp.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("Failed to create collection: " + createResp.getMessage());
        }
        
        log.info("Collection '{}' created successfully", collectionName);
        
        // 创建索引
        createIndex();
        
        // 加载 Collection
        loadCollection();
    }
    
    /**
     * 创建向量索引
     */
    private void createIndex() {
        log.info("Creating index for collection '{}'", collectionName);
        
        // 构建索引参数
        JsonObject indexParams = new JsonObject();
        indexParams.addProperty("nlist", nlist);
        
        CreateIndexParam createIndexParam = CreateIndexParam.newBuilder()
                .withCollectionName(collectionName)
                .withFieldName(FIELD_VECTOR)
                .withIndexType(IndexType.valueOf(indexType))
                .withMetricType(MetricType.valueOf(metricType))
                .withExtraParam(indexParams.toString())
                .withSyncMode(Boolean.TRUE)
                .build();
        
        R<RpcStatus> createIndexResp = milvusClient.createIndex(createIndexParam);
        if (createIndexResp.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("Failed to create index: " + createIndexResp.getMessage());
        }
        
        log.info("Index created successfully");
    }
    
    /**
     * 加载 Collection 到内存
     */
    private void loadCollection() {
        log.info("Loading collection '{}' into memory", collectionName);
        
        R<RpcStatus> loadResp = milvusClient.loadCollection(
                LoadCollectionParam.newBuilder()
                        .withCollectionName(collectionName)
                        .build()
        );
        
        if (loadResp.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("Failed to load collection: " + loadResp.getMessage());
        }
        
        log.info("Collection loaded successfully");
    }
    
    /**
     * 存储文档的向量数据
     * 注意：调用此方法前，DocumentChunk 对象应该已经包含了生成的向量数据
     * 向量数据存储在 metadata 字段中的 "embedding" 键下
     */
    @Override
    public void storeVectors(Long documentId, List<DocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            log.warn("No chunks to store for document: {}", documentId);
            return;
        }
        
        log.info("Storing {} vectors for document: {}", chunks.size(), documentId);
        
        try {
            // 准备数据
            List<Long> documentIds = new ArrayList<>();
            List<Long> chunkIds = new ArrayList<>();
            List<Integer> chunkIndexes = new ArrayList<>();
            List<String> contents = new ArrayList<>();
            List<List<Float>> vectors = new ArrayList<>();
            List<String> metadataList = new ArrayList<>();
            
            for (DocumentChunk chunk : chunks) {
                documentIds.add(documentId);
                chunkIds.add(chunk.getId());
                chunkIndexes.add(chunk.getChunkIndex());
                contents.add(chunk.getContent());
                
                // 从 metadata 中提取向量数据
                // 实际使用时，向量应该由 EmbeddingService 生成并存储在 metadata 中
                List<Float> vector = extractVectorFromMetadata(chunk.getMetadata());
                if (vector.isEmpty()) {
                    log.error("No vector data found for chunk: {}", chunk.getId());
                    throw new IllegalArgumentException("Chunk must contain vector data in metadata");
                }
                vectors.add(vector);
                
                // 存储不包含向量的元数据（向量已单独存储）
                String cleanMetadata = removeVectorFromMetadata(chunk.getMetadata());
                metadataList.add(cleanMetadata != null ? cleanMetadata : "{}");
            }
            
            // 插入数据
            List<InsertParam.Field> fields = Arrays.asList(
                    new InsertParam.Field(FIELD_DOCUMENT_ID, documentIds),
                    new InsertParam.Field(FIELD_CHUNK_ID, chunkIds),
                    new InsertParam.Field(FIELD_CHUNK_INDEX, chunkIndexes),
                    new InsertParam.Field(FIELD_CONTENT, contents),
                    new InsertParam.Field(FIELD_VECTOR, vectors),
                    new InsertParam.Field(FIELD_METADATA, metadataList)
            );
            
            InsertParam insertParam = InsertParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withFields(fields)
                    .build();
            
            R<MutationResult> insertResp = milvusClient.insert(insertParam);
            if (insertResp.getStatus() != R.Status.Success.getCode()) {
                throw new RuntimeException("Failed to insert vectors: " + insertResp.getMessage());
            }
            
            log.info("Successfully stored {} vectors for document: {}", chunks.size(), documentId);
            
        } catch (Exception e) {
            log.error("Failed to store vectors for document: {}", documentId, e);
            throw new RuntimeException("Failed to store vectors", e);
        }
    }
    
    /**
     * 相似度检索
     * 使用 @CircuitBreaker 实现熔断降级
     */
    @Override
    @CircuitBreaker(name = "vectorStoreService", fallbackMethod = "searchFallback")
    public List<SearchResult> search(float[] queryVector, int topK, Long documentId) {
        log.debug("Searching for top {} similar vectors", topK);
        
        try {
            // 将 float[] 转换为 List<Float>
            List<Float> vectorList = new ArrayList<>();
            for (float v : queryVector) {
                vectorList.add(v);
            }
            List<List<Float>> vectors = Collections.singletonList(vectorList);
            
            // 构建搜索参数
            JsonObject searchParams = new JsonObject();
            searchParams.addProperty("nprobe", nprobe);
            
            // 构建过滤表达式（如果指定了 documentId）
            String expr = documentId != null ? FIELD_DOCUMENT_ID + " == " + documentId : "";
            
            SearchParam.Builder searchBuilder = SearchParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withMetricType(MetricType.valueOf(metricType))
                    .withOutFields(Arrays.asList(
                            FIELD_DOCUMENT_ID,
                            FIELD_CHUNK_ID,
                            FIELD_CHUNK_INDEX,
                            FIELD_CONTENT,
                            FIELD_METADATA
                    ))
                    .withTopK(topK)
                    .withVectors(vectors)
                    .withVectorFieldName(FIELD_VECTOR)
                    .withParams(searchParams.toString())
                    .withConsistencyLevel(ConsistencyLevelEnum.STRONG);
            
            if (!expr.isEmpty()) {
                searchBuilder.withExpr(expr);
            }
            
            R<SearchResults> searchResp = milvusClient.search(searchBuilder.build());
            if (searchResp.getStatus() != R.Status.Success.getCode()) {
                throw new RuntimeException("Failed to search vectors: " + searchResp.getMessage());
            }
            
            // 解析搜索结果
            SearchResultsWrapper wrapper = new SearchResultsWrapper(searchResp.getData().getResults());
            List<SearchResult> results = new ArrayList<>();
            
            for (int i = 0; i < wrapper.getIDScore(0).size(); i++) {
                SearchResultsWrapper.IDScore idScore = wrapper.getIDScore(0).get(i);
                
                SearchResult result = SearchResult.builder()
                        .documentId((Long) wrapper.getFieldData(FIELD_DOCUMENT_ID, 0).get(i))
                        .chunkId((Long) wrapper.getFieldData(FIELD_CHUNK_ID, 0).get(i))
                        .chunkIndex((Integer) wrapper.getFieldData(FIELD_CHUNK_INDEX, 0).get(i))
                        .content((String) wrapper.getFieldData(FIELD_CONTENT, 0).get(i))
                        .score(idScore.getScore())
                        .metadata(parseMetadata((String) wrapper.getFieldData(FIELD_METADATA, 0).get(i)))
                        .build();
                
                results.add(result);
            }
            
            log.debug("Found {} results", results.size());
            return results;
            
        } catch (Exception e) {
            log.error("Failed to search vectors", e);
            throw new RuntimeException("Failed to search vectors", e);
        }
    }
    
    /**
     * 删除文档的所有向量（幂等操作）
     * 
     * 幂等性保证：
     * 1. 如果向量不存在，返回成功（删除计数为0）
     * 2. 多次调用产生相同结果
     * 3. 不会抛出异常（除非系统错误）
     * 
     * @param documentId 文档 ID
     */
    @Override
    public void deleteVectors(Long documentId) {
        log.info("Deleting vectors for document: {} (idempotent operation)", documentId);
        
        try {
            // 先检查向量是否存在（用于日志记录）
            boolean exists = exists(documentId);
            
            if (!exists) {
                log.info("No vectors found for document: {}, skipping deletion (idempotent)", documentId);
                return; // 幂等性：不存在时直接返回成功
            }
            
            // 构建删除表达式
            String expr = FIELD_DOCUMENT_ID + " == " + documentId;
            
            DeleteParam deleteParam = DeleteParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withExpr(expr)
                    .build();
            
            R<MutationResult> deleteResp = milvusClient.delete(deleteParam);
            if (deleteResp.getStatus() != R.Status.Success.getCode()) {
                throw new RuntimeException("Failed to delete vectors: " + deleteResp.getMessage());
            }
            
            long deleteCount = deleteResp.getData().getDeleteCnt();
            log.info("Successfully deleted {} vectors for document: {}", deleteCount, documentId);
            
            // 验证删除结果（确保幂等性）
            boolean stillExists = exists(documentId);
            if (stillExists) {
                log.warn("Vectors still exist after deletion for document: {}, may need retry", documentId);
            }
            
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
            // 构建查询表达式
            String expr = FIELD_DOCUMENT_ID + " == " + documentId;
            
            QueryParam queryParam = QueryParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withExpr(expr)
                    .withOutFields(Collections.singletonList(FIELD_DOCUMENT_ID))
                    .build();
            
            R<QueryResults> queryResp = milvusClient.query(queryParam);
            if (queryResp.getStatus() != R.Status.Success.getCode()) {
                throw new RuntimeException("Failed to query vectors: " + queryResp.getMessage());
            }
            
            QueryResultsWrapper wrapper = new QueryResultsWrapper(queryResp.getData());
            boolean exists = wrapper.getFieldWrapper(FIELD_DOCUMENT_ID).getFieldData().size() > 0;
            
            log.debug("Vectors exist for document {}: {}", documentId, exists);
            return exists;
            
        } catch (Exception e) {
            log.error("Failed to check if vectors exist for document: {}", documentId, e);
            throw new RuntimeException("Failed to check vector existence", e);
        }
    }
    
    /**
     * 解析元数据 JSON
     */
    private Map<String, Object> parseMetadata(String metadataJson) {
        try {
            if (metadataJson == null || metadataJson.isEmpty() || "{}".equals(metadataJson)) {
                return new HashMap<>();
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> result = gson.fromJson(metadataJson, Map.class);
            return result;
        } catch (Exception e) {
            log.warn("Failed to parse metadata JSON: {}", metadataJson, e);
            return new HashMap<>();
        }
    }
    
    /**
     * 从 metadata JSON 中提取向量数据
     * 向量数据应该存储在 metadata 的 "embedding" 键下
     */
    private List<Float> extractVectorFromMetadata(String metadataJson) {
        try {
            if (metadataJson == null || metadataJson.isEmpty()) {
                return Collections.emptyList();
            }
            
            JsonObject metadata = gson.fromJson(metadataJson, JsonObject.class);
            if (metadata == null || !metadata.has("embedding")) {
                return Collections.emptyList();
            }
            
            // 提取向量数组
            List<Float> vector = new ArrayList<>();
            metadata.getAsJsonArray("embedding").forEach(element -> 
                vector.add(element.getAsFloat())
            );
            
            return vector;
        } catch (Exception e) {
            log.error("Failed to extract vector from metadata: {}", metadataJson, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 从 metadata JSON 中移除向量数据
     * 返回不包含向量的元数据 JSON
     */
    private String removeVectorFromMetadata(String metadataJson) {
        try {
            if (metadataJson == null || metadataJson.isEmpty()) {
                return "{}";
            }
            
            JsonObject metadata = gson.fromJson(metadataJson, JsonObject.class);
            if (metadata == null) {
                return "{}";
            }
            
            // 移除 embedding 字段
            metadata.remove("embedding");
            
            return gson.toJson(metadata);
        } catch (Exception e) {
            log.error("Failed to remove vector from metadata: {}", metadataJson, e);
            return "{}";
        }
    }
    
    /**
     * 关闭 Milvus 客户端
     */
    @PreDestroy
    public void destroy() {
        if (milvusClient != null) {
            log.info("Closing Milvus client");
            milvusClient.close();
        }
    }

    /**
     * 向量检索降级方法
     * 当向量库不可用时返回空结果
     */
    private List<SearchResult> searchFallback(float[] queryVector, int topK, Long documentId, Throwable throwable) {
        log.warn("Vector store service degraded, returning empty search results. Error: {}", throwable.getMessage());
        return Collections.emptyList();
    }
}
