# AI助手RAG系统 - 技术实现详解

## 📋 目录

1. [项目概述](#项目概述)
2. [核心技术架构](#核心技术架构)
3. [依赖管理与迁移](#依赖管理与迁移)
4. [模块详解](#模块详解)
5. [核心功能实现](#核心功能实现)
6. [面试准备](#面试准备)

---

## 1. 项目概述

### 1.1 系统简介

MallChat AI助手RAG系统是一个基于检索增强生成（Retrieval-Augmented Generation）技术的智能问答系统，集成了文档处理、向量检索、大语言模型等核心能力。

### 1.2 技术栈

- **后端框架**: Spring Boot 2.6.13
- **AI框架**: LangChain4j（替代Spring AI）
- **向量数据库**: Milvus 2.3.x
- **大语言模型**: OpenAI GPT-3.5/GPT-4
- **文档处理**: Apache Tika
- **消息队列**: RocketMQ
- **缓存**: JetCache (Redis + Caffeine)
- **响应式编程**: Project Reactor

### 1.3 核心特性

✅ **流式响应**: 基于Reactor实现的实时流式输出  
✅ **向量检索**: Milvus向量数据库高性能检索  
✅ **文档处理**: 支持PDF、Word、TXT等多种格式  
✅ **异步索引**: RocketMQ异步文档索引处理  
✅ **熔断降级**: Resilience4j实现的服务保护  
✅ **限流控制**: 基于Redis的分布式限流

---

## 2. 核心技术架构

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────┐
│                      前端 (Vue.js)                           │
│                  WebSocket + HTTP API                        │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────┐
│                  Controller Layer                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ RAGController│  │DocumentCtrl  │  │ ChatCtrl     │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────┐
│                   Service Layer                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ RAGService   │  │DocumentProc  │  │ LLMService   │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────┴────────────────────────────────────────┐
│              Infrastructure Layer                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ Milvus Vector│  │ RocketMQ     │  │ Redis Cache  │      │
│  │   Database   │  │   Queue      │  │  (JetCache)  │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
│  ┌──────────────┐  ┌──────────────┐                        │
│  │ OpenAI API   │  │ MySQL DB     │                        │
│  └──────────────┘  └──────────────┘                        │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 模块划分

```
mallchat-ai/
├── mallchat-ai-common/      # 公共模块（实体、DAO、枚举）
├── mallchat-ai-llm/         # LLM服务模块
├── mallchat-ai-vector/      # 向量服务模块
├── mallchat-ai-rag/         # RAG核心模块
└── mallchat-ai-assistant/   # AI助手模块
```

---

## 3. 依赖管理与迁移

### 3.1 为什么从Spring AI迁移到LangChain4j？

#### 问题背景

项目初期使用Spring AI，但遇到以下问题：

1. **依赖冲突**: Spring AI与Spring Boot 2.6.x版本不兼容
2. **功能限制**: Spring AI功能较新，生态不够成熟
3. **文档缺失**: 官方文档不完善，社区支持有限

#### 迁移优势

| 对比项            | Spring AI | LangChain4j |
| ----------------- | --------- | ----------- |
| Spring Boot兼容性 | 需要3.x   | 支持2.x     |
| 功能完整度        | 基础功能  | 功能丰富    |
| 社区活跃度        | 较新      | 活跃        |
| 文档质量          | 一般      | 优秀        |
| 流式支持          | 有限      | 完善        |

### 3.2 核心依赖配置

#### 父POM配置 (mallchat-ai/pom.xml)

```xml
<properties>
    <langchain4j.version>0.27.1</langchain4j.version>
    <milvus.version>2.3.4</milvus.version>
    <tika.version>2.9.1</tika.version>
</properties>

<dependencyManagement>
    <dependencies>
        <!-- LangChain4j Core -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>

        <!-- LangChain4j OpenAI -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-open-ai</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>

        <!-- LangChain4j Embeddings -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-embeddings</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### 3.3 迁移步骤

#### Step 1: 删除Spring AI依赖

```bash
# 删除所有Spring AI相关依赖
# 删除 SpringAIConfig.java 配置类
```

#### Step 2: 添加LangChain4j依赖

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
</dependency>
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
</dependency>
```

#### Step 3: 修改配置类

创建 `LangChain4jConfig.java` 替代原有配置

#### Step 4: 重构Service层

将Spring AI的API调用改为LangChain4j API

---

## 4. 模块详解

### 4.1 mallchat-ai-llm 模块

#### 4.1.1 模块职责

- 封装大语言模型调用
- 提供流式和非流式接口
- Token计数和管理
- 熔断降级处理

#### 4.1.2 核心类结构

```java
com.abin.mallchat.ai.llm/
├── config/
│   ├── LangChain4jConfig.java      # LangChain4j配置
│   └── LLMConfig.java               # LLM参数配置
├── service/
│   ├── LLMService.java              # LLM服务接口
│   └── impl/
│       └── OpenAILLMService.java    # OpenAI实现
├── domain/
│   └── LLMOptions.java              # LLM调用选项
└── exception/
    └── LLMApiException.java         # LLM异常
```

#### 4.1.3 LangChain4jConfig 配置详解

```java
@Configuration
public class LangChain4jConfig {

    @Value("${langchain4j.openai.api-key}")
    private String apiKey;

    @Value("${langchain4j.openai.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    /**
     * 配置同步 Chat Language Model
     * 用于非流式的 LLM 调用
     */
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName("gpt-3.5-turbo")
                .temperature(0.7)
                .maxTokens(2000)
                .timeout(Duration.ofSeconds(60))
                .maxRetries(3)
                .logRequests(true)
                .logResponses(false)
                .build();
    }

    /**
     * 配置流式 Chat Language Model
     * 用于流式输出的 LLM 调用
     */
    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel() {
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName("gpt-3.5-turbo")
                .temperature(0.7)
                .maxTokens(2000)
                .timeout(Duration.ofSeconds(60))
                .logRequests(true)
                .logResponses(false)
                .build();
    }

    /**
     * 配置 OpenAI Tokenizer
     * 用于估算文本的 token 数量
     */
    @Bean
    public OpenAiTokenizer openAiTokenizer() {
        return new OpenAiTokenizer("gpt-3.5-turbo");
    }
}
```

**关键配置说明**:

- `apiKey`: OpenAI API密钥
- `baseUrl`: API基础URL（支持代理）
- `temperature`: 控制输出随机性（0-2，越高越随机）
- `maxTokens`: 最大输出token数
- `timeout`: 请求超时时间
- `maxRetries`: 失败重试次数

#### 4.1.4 OpenAILLMService 实现详解

```java
@Service
public class OpenAILLMService implements LLMService {

    @Autowired
    private ChatLanguageModel chatLanguageModel;

    @Autowired
    private StreamingChatLanguageModel streamingChatLanguageModel;

    @Autowired
    private OpenAiTokenizer tokenizer;

    /**
     * 流式调用 LLM
     * 使用 Reactor Flux 实现流式响应
     */
    @Override
    @CircuitBreaker(name = "llmService", fallbackMethod = "streamChatFallback")
    @Retryable(value = {LLMApiException.class}, maxAttempts = 3)
    public Flux<String> streamChat(String prompt, LLMOptions options) {
        return Flux.create(sink -> {
            streamingChatLanguageModel.generate(
                prompt,
                new StreamingResponseHandler<AiMessage>() {
                    @Override
                    public void onNext(String token) {
                        if (token != null && !token.isEmpty()) {
                            sink.next(token);  // 实时推送token
                        }
                    }

                    @Override
                    public void onComplete(Response<AiMessage> response) {
                        sink.complete();  // 完成流
                    }

                    @Override
                    public void onError(Throwable error) {
                        sink.error(new LLMApiException("Stream chat failed", error));
                    }
                }
            );
        });
    }

    /**
     * 非流式调用 LLM
     */
    @Override
    @CircuitBreaker(name = "llmService", fallbackMethod = "chatFallback")
    @Retryable(value = {LLMApiException.class}, maxAttempts = 3)
    public String chat(String prompt, LLMOptions options) {
        String response = chatLanguageModel.generate(prompt);

        if (response != null && !response.isEmpty()) {
            return response;
        }

        throw new LLMApiException("Empty response from LLM");
    }

    /**
     * 计算 token 数量
     */
    @Override
    public int countTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        try {
            return tokenizer.estimateTokenCountInText(text);
        } catch (Exception e) {
            // 降级方案：简单估算
            return estimateTokensSimple(text);
        }
    }

    /**
     * 简单的 token 估算算法（降级方案）
     */
    private int estimateTokensSimple(String text) {
        int chineseCount = 0;
        int englishWords = 0;

        for (char c : text.toCharArray()) {
            if (c >= 0x4E00 && c <= 0x9FA5) {
                chineseCount++;
            }
        }

        String[] words = text.split("\\s+");
        for (String word : words) {
            if (word.matches("[a-zA-Z]+")) {
                englishWords++;
            }
        }

        // 中文：1字符≈1.5 token，英文：1单词≈1.3 token
        return (int) (chineseCount * 1.5 + englishWords * 1.3);
    }

    /**
     * 流式调用降级方法
     */
    private Flux<String> streamChatFallback(String prompt, LLMOptions options, Throwable throwable) {
        log.warn("LLM service degraded: {}", throwable.getMessage());
        return Flux.just("抱歉，AI服务暂时不可用，请稍后再试。");
    }

    /**
     * 非流式调用降级方法
     */
    private String chatFallback(String prompt, LLMOptions options, Throwable throwable) {
        log.warn("LLM service degraded: {}", throwable.getMessage());
        return "抱歉，AI服务暂时不可用，请稍后再试。";
    }
}
```

**核心技术点**:

1. **流式响应实现**
   - 使用 `Flux.create` 创建响应式流
   - `StreamingResponseHandler` 处理流式回调
   - `sink.next()` 实时推送每个token
   - `sink.complete()` 标记流结束

2. **熔断降级**
   - `@CircuitBreaker` 注解实现熔断
   - `fallbackMethod` 指定降级方法
   - 服务不可用时返回友好提示

3. **重试机制**
   - `@Retryable` 注解实现自动重试
   - `maxAttempts = 3` 最多重试3次
   - `@Backoff` 配置退避策略

4. **Token计数**
   - 使用 `OpenAiTokenizer` 精确计数
   - 降级方案：简单估算算法
   - 中英文分别计算

---

### 4.2 mallchat-ai-vector 模块

#### 4.2.1 模块职责

- 向量数据库操作封装
- 文本向量化（Embedding）
- 向量相似度检索
- 向量数据管理

#### 4.2.2 核心类结构

```java
com.abin.mallchat.ai.vector/
├── config/
│   └── MilvusConfig.java            # Milvus配置
├── service/
│   ├── VectorService.java           # 向量服务接口
│   ├── EmbeddingService.java        # 向量化服务接口
│   └── impl/
│       ├── MilvusVectorService.java # Milvus实现
│       └── OpenAIEmbeddingService.java # OpenAI Embedding
└── domain/
    ├── SearchResult.java            # 检索结果
    └── VectorData.java              # 向量数据
```

#### 4.2.3 MilvusVectorService 实现

```java
@Service
public class MilvusVectorService implements VectorService {

    @Autowired
    private MilvusServiceClient milvusClient;

    @Autowired
    private EmbeddingService embeddingService;

    private static final String COLLECTION_NAME = "document_chunks";
    private static final int VECTOR_DIM = 1536;  // OpenAI embedding维度

    /**
     * 初始化集合
     */
    @PostConstruct
    public void initCollection() {
        if (!collectionExists()) {
            createCollection();
            createIndex();
        }
    }

    /**
     * 创建集合
     */
    private void createCollection() {
        FieldType chunkIdField = FieldType.newBuilder()
                .withName("chunk_id")
                .withDataType(DataType.Int64)
                .withPrimaryKey(true)
                .withAutoID(false)
                .build();

        FieldType vectorField = FieldType.newBuilder()
                .withName("embedding")
                .withDataType(DataType.FloatVector)
                .withDimension(VECTOR_DIM)
                .build();

        FieldType contentField = FieldType.newBuilder()
                .withName("content")
                .withDataType(DataType.VarChar)
                .withMaxLength(65535)
                .build();

        CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withDescription("Document chunks collection")
                .withFieldTypes(Arrays.asList(chunkIdField, vectorField, contentField))
                .build();

        milvusClient.createCollection(createParam);
    }

    /**
     * 创建索引
     */
    private void createIndex() {
        CreateIndexParam indexParam = CreateIndexParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withFieldName("embedding")
                .withIndexType(IndexType.IVF_FLAT)
                .withMetricType(MetricType.L2)
                .withExtraParam("{\"nlist\":1024}")
                .build();

        milvusClient.createIndex(indexParam);
    }

    /**
     * 插入向量
     */
    @Override
    public void insert(Long chunkId, String content) {
        // 1. 文本向量化
        List<Float> embedding = embeddingService.embed(content);

        // 2. 构建插入数据
        List<InsertParam.Field> fields = new ArrayList<>();
        fields.add(new InsertParam.Field("chunk_id", Collections.singletonList(chunkId)));
        fields.add(new InsertParam.Field("embedding", Collections.singletonList(embedding)));
        fields.add(new InsertParam.Field("content", Collections.singletonList(content)));

        // 3. 执行插入
        InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withFields(fields)
                .build();

        milvusClient.insert(insertParam);
    }

    /**
     * 向量检索
     */
    @Override
    public List<SearchResult> search(String query, int topK) {
        // 1. 查询文本向量化
        List<Float> queryEmbedding = embeddingService.embed(query);

        // 2. 构建检索参数
        SearchParam searchParam = SearchParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withMetricType(MetricType.L2)
                .withTopK(topK)
                .withVectors(Collections.singletonList(queryEmbedding))
                .withVectorFieldName("embedding")
                .withParams("{\"nprobe\":10}")
                .build();

        // 3. 执行检索
        SearchResults results = milvusClient.search(searchParam);

        // 4. 解析结果
        return parseSearchResults(results);
    }

    /**
     * 删除向量
     */
    @Override
    public void delete(Long chunkId) {
        String expr = "chunk_id == " + chunkId;

        DeleteParam deleteParam = DeleteParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withExpr(expr)
                .build();

        milvusClient.delete(deleteParam);
    }
}
```

**核心技术点**:

1. **集合设计**
   - `chunk_id`: 主键，关联文档块
   - `embedding`: 1536维向量（OpenAI）
   - `content`: 原始文本内容

2. **索引选择**
   - `IVF_FLAT`: 倒排文件索引
   - `L2`: 欧氏距离度量
   - `nlist=1024`: 聚类中心数量

3. **检索流程**
   - 查询文本 → 向量化
   - 向量检索 → Top-K结果
   - 结果解析 → 返回相关文档

#### 4.2.4 OpenAIEmbeddingService 实现

```java
@Service
public class OpenAIEmbeddingService implements EmbeddingService {

    @Autowired
    private EmbeddingModel embeddingModel;

    /**
     * 文本向量化
     */
    @Override
    @CircuitBreaker(name = "embeddingService", fallbackMethod = "embedFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3)
    public List<Float> embed(String text) {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("Text cannot be empty");
        }

        try {
            // 调用 LangChain4j Embedding API
            Response<Embedding> response = embeddingModel.embed(text);
            Embedding embedding = response.content();

            // 转换为 Float 列表
            return embedding.vectorAsList();

        } catch (Exception e) {
            log.error("Failed to embed text", e);
            throw new EmbeddingException("Failed to embed text", e);
        }
    }

    /**
     * 批量向量化
     */
    @Override
    public List<List<Float>> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            Response<List<Embedding>> response = embeddingModel.embedAll(texts);
            List<Embedding> embeddings = response.content();

            return embeddings.stream()
                    .map(Embedding::vectorAsList)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Failed to embed batch", e);
            throw new EmbeddingException("Failed to embed batch", e);
        }
    }

    /**
     * 降级方法
     */
    private List<Float> embedFallback(String text, Throwable throwable) {
        log.warn("Embedding service degraded: {}", throwable.getMessage());
        // 返回零向量
        return Collections.nCopies(1536, 0.0f);
    }
}
```

---

### 4.3 mallchat-ai-rag 模块

#### 4.3.1 模块职责

- RAG核心流程编排
- 文档处理与分块
- 异步索引处理
- 查询增强与生成

#### 4.3.2 核心类结构

```java
com.abin.mallchat.ai.rag/
├── controller/
│   ├── RAGController.java           # RAG接口
│   └── DocumentController.java      # 文档管理接口
├── service/
│   ├── RAGService.java              # RAG服务接口
│   ├── DocumentProcessingService.java # 文档处理接口
│   ├── DocumentIndexingProducer.java  # 索引生产者
│   └── impl/
│       ├── RAGServiceImpl.java      # RAG实现
│       └── TikaDocumentProcessingService.java # Tika实现
├── consumer/
│   └── DocumentIndexingConsumer.java # 索引消费者
├── domain/
│   ├── dto/
│   │   ├── RAGQueryRequest.java     # 查询请求
│   │   ├── StreamChunk.java         # 流式数据块
│   │   └── DocumentMetadata.java    # 文档元数据
│   └── entity/
│       └── ChunkMetadata.java       # 分块元数据
└── config/
    └── DocumentConfig.java          # 文档配置
```

#### 4.3.3 RAGServiceImpl 核心实现

```java
@Service
public class RAGServiceImpl implements RAGService {

    @Autowired
    private VectorService vectorService;

    @Autowired
    private LLMService llmService;

    @Autowired
    private DocumentChunkDao documentChunkDao;

    /**
     * RAG查询 - 流式响应
     */
    @Override
    public Flux<StreamChunk> queryStream(RAGQueryRequest request) {
        return Flux.create(sink -> {
            try {
                // 1. 向量检索相关文档
                List<SearchResult> searchResults = vectorService.search(
                    request.getQuery(),
                    request.getTopK()
                );

                // 2. 构建增强提示词
                String enhancedPrompt = buildEnhancedPrompt(
                    request.getQuery(),
                    searchResults
                );

                // 3. 发送检索到的文档块
                for (SearchResult result : searchResults) {
                    sink.next(StreamChunk.builder()
                            .type("context")
                            .content(result.getContent())
                            .score(result.getScore())
                            .build());
                }

                // 4. 流式调用LLM
                llmService.streamChat(enhancedPrompt, request.getLlmOptions())
                        .subscribe(
                            token -> sink.next(StreamChunk.builder()
                                    .type("answer")
                                    .content(token)
                                    .build()),
                            error -> sink.error(error),
                            () -> sink.complete()
                        );

            } catch (Exception e) {
                log.error("RAG query failed", e);
                sink.error(new RAGException("RAG query failed", e));
            }
        });
    }

    /**
     * 构建增强提示词
     */
    private String buildEnhancedPrompt(String query, List<SearchResult> contexts) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("请基于以下参考信息回答问题：\n\n");

        // 添加检索到的上下文
        for (int i = 0; i < contexts.size(); i++) {
            prompt.append("参考资料 ").append(i + 1).append(":\n");
            prompt.append(contexts.get(i).getContent()).append("\n\n");
        }

        prompt.append("问题: ").append(query).append("\n\n");
        prompt.append("请根据上述参考资料回答问题。如果参考资料中没有相关信息，请明确说明。");

        return prompt.toString();
    }

    /**
     * RAG查询 - 非流式
     */
    @Override
    public String query(RAGQueryRequest request) {
        // 1. 向量检索
        List<SearchResult> searchResults = vectorService.search(
            request.getQuery(),
            request.getTopK()
        );

        // 2. 构建增强提示词
        String enhancedPrompt = buildEnhancedPrompt(
            request.getQuery(),
            searchResults
        );

        // 3. 调用LLM
        return llmService.chat(enhancedPrompt, request.getLlmOptions());
    }
}
```

**RAG流程详解**:

```
用户查询
    ↓
1. 向量检索 (Vector Search)
    ├─ 查询文本向量化
    ├─ Milvus相似度检索
    └─ 返回Top-K相关文档
    ↓
2. 提示词增强 (Prompt Enhancement)
    ├─ 组装检索到的上下文
    ├─ 构建结构化提示词
    └─ 添加查询问题
    ↓
3. LLM生成 (Generation)
    ├─ 流式调用LLM
    ├─ 实时推送token
    └─ 返回最终答案
    ↓
流式响应给前端
```

#### 4.3.4 文档处理与分块

```java
@Service
public class TikaDocumentProcessingService implements DocumentProcessingService {

    @Autowired
    private ChunkStrategy chunkStrategy;

    private static final int MAX_CHUNK_SIZE = 500;
    private static final int CHUNK_OVERLAP = 50;

    /**
     * 处理文档
     */
    @Override
    public List<DocumentChunk> processDocument(MultipartFile file, Long documentId) {
        try {
            // 1. 提取文本
            String content = extractText(file);

            // 2. 文本分块
            List<String> chunks = chunkStrategy.chunk(content, MAX_CHUNK_SIZE, CHUNK_OVERLAP);

            // 3. 创建文档块实体
            List<DocumentChunk> documentChunks = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                DocumentChunk chunk = new DocumentChunk();
                chunk.setDocumentId(documentId);
                chunk.setChunkIndex(i);
                chunk.setContent(chunks.get(i));
                chunk.setTokenCount(llmService.countTokens(chunks.get(i)));
                documentChunks.add(chunk);
            }

            return documentChunks;

        } catch (Exception e) {
            log.error("Failed to process document", e);
            throw new DocumentProcessingException("Failed to process document", e);
        }
    }

    /**
     * 使用 Apache Tika 提取文本
     */
    private String extractText(MultipartFile file) throws Exception {
        Tika tika = new Tika();
        try (InputStream inputStream = file.getInputStream()) {
            return tika.parseToString(inputStream);
        }
    }
}
```

**文档分块策略**:

```java
@Component
public class FixedSizeChunkStrategy implements ChunkStrategy {

    @Override
    public List<String> chunk(String text, int maxSize, int overlap) {
        List<String> chunks = new ArrayList<>();

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxSize, text.length());

            // 尝试在句子边界分割
            if (end < text.length()) {
                int lastPeriod = text.lastIndexOf('。', end);
                int lastNewline = text.lastIndexOf('\n', end);
                int boundary = Math.max(lastPeriod, lastNewline);

                if (boundary > start) {
                    end = boundary + 1;
                }
            }

            chunks.add(text.substring(start, end));
            start = end - overlap;  // 重叠部分
        }

        return chunks;
    }
}
```

**分块策略说明**:

- **固定大小**: 每块最多500字符
- **重叠处理**: 相邻块重叠50字符，保持上下文连贯
- **边界优化**: 尽量在句子边界分割，避免截断
