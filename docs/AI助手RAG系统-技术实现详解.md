# AI助手RAG系统 - 技术实现详解

> **⚠️ 文档更新提示**：本文档为 RAG 系统技术实现速查版，随代码从 OpenAI/Milvus 方案迁移到 **Ollama + Qdrant + bge + Qwen** 本地开源方案后已同步更新。完整技术方案、部署指南和接口文档请参见：
> - [mallchat-ai/docs/AI技术方案.md](../mallchat-ai/docs/AI技术方案.md)
> - [mallchat-ai/mallchat-ai-rag/docs/架构设计详解.md](../mallchat-ai/mallchat-ai-rag/docs/架构设计详解.md)
> - [mallchat-ai/mallchat-ai-rag/docs/API接口文档.md](../mallchat-ai/mallchat-ai-rag/docs/API接口文档.md)
> - [mallchat-ai/mallchat-ai-rag/docs/部署运维指南.md](../mallchat-ai/mallchat-ai-rag/docs/部署运维指南.md)

---

## 📋 目录

1. [项目概述](#1-项目概述)
2. [核心技术架构](#2-核心技术架构)
3. [依赖管理与迁移](#3-依赖管理与迁移)
4. [核心模块实现](#4-核心模块实现)
5. [RAG 查询流程详解](#5-rag-查询流程详解)
6. [面试准备](#6-面试准备)

---

## 1. 项目概述

### 1.1 系统简介

MallChat AI助手RAG系统是一个基于检索增强生成（Retrieval-Augmented Generation）技术的智能问答系统，集成了文档处理、向量检索、大语言模型等核心能力。

### 1.2 技术栈

- **后端框架**: Spring Boot 2.7.x
- **AI框架**: LangChain4j 0.36.0（替代Spring AI）
- **向量数据库**: Qdrant 1.8+（默认，动态向量）；Milvus 2.3+（备选）
- **大语言模型**: Qwen2.5-14B / Llama3-70B via Ollama；OpenAI / ChatGLM 兼容
- **Embedding模型**: bge-large-zh-v1.5（1024维）/ m3e-base（768维）
- **文档处理**: Apache Tika
- **消息队列**: RocketMQ
- **缓存**: JetCache (Redis + Caffeine)
- **响应式编程**: Project Reactor
- **Mock模式**: 完整 Mock 实现，无需外部依赖即可启动

### 1.3 核心特性

✅ **流式响应**: 基于Reactor + SSE实现的实时流式输出  
✅ **向量检索**: Qdrant动态向量，兼容多种Embedding维度  
✅ **文档处理**: 支持PDF、Word、TXT、MD、HTML等多种格式  
✅ **异步索引**: RocketMQ异步文档索引处理  
✅ **熔断降级**: Resilience4j实现的服务保护  
✅ **限流控制**: 基于Redis的分布式限流  
✅ **多轮对话**: AI助手支持sessionId会话管理  
✅ **Mock模式**: 本地无依赖启动

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
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐ │
│  │ AIAssistant  │  │DocumentCtrl  │  │ StreamController │ │
│  │  Controller  │  │  /documents/* │  │  /stream/*       │ │
│  └──────────────┘  └──────────────┘  └──────────────────┘ │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────┐
│                   Service Layer                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ AIAssistant  │  │ RAGService   │  │ LLMService   │      │
│  │   Service    │  │              │  │   Factory    │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────┴────────────────────────────────────────┐
│              Infrastructure Layer                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ Qdrant       │  │ RocketMQ     │  │ Redis Cache  │      │
│  │   Vector     │  │   Queue      │  │  (JetCache)  │      │
│  │   Database   │  │              │  │              │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ Ollama       │  │ MySQL DB     │  │ Milvus       │      │
│  │ (LLM/Embed)  │  │              │  │ 备选         │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 模块划分

```
mallchat-ai/
├── mallchat-ai-common/      # 公共模块（实体、DAO、枚举）
├── mallchat-ai-llm/         # LLM服务模块
├── mallchat-ai-vector/      # 向量服务模块
├── mallchat-ai-rag/         # RAG核心模块
├── mallchat-ai-assistant/   # AI助手模块
└── mallchat-ai-finetune/    # 微调框架模块
```

---

## 3. 依赖管理与迁移

### 3.1 为什么从Spring AI迁移到LangChain4j？

项目初期考虑过Spring AI，但遇到以下问题：

1. **依赖冲突**: Spring AI与Spring Boot 2.x版本不兼容
2. **功能限制**: Spring AI功能较新，生态不够成熟
3. **文档缺失**: 官方文档不完善，社区支持有限
4. **本地模型支持弱**: 对 Ollama、本地 Embedding 支持不如 LangChain4j

### 3.2 核心依赖配置

#### 父POM配置 (mallchat-ai/pom.xml)

```xml
<properties>
    <langchain4j.version>0.36.0</langchain4j.version>
    <langchain4j-spring-boot.version>0.36.0</langchain4j-spring-boot.version>
    <milvus-sdk.version>2.3.4</milvus-sdk.version>
    <qdrant-java-client.version>1.14.0</qdrant-java-client.version>
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

        <!-- LangChain4j Ollama -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-ollama</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>

        <!-- Qdrant -->
        <dependency>
            <groupId>io.qdrant</groupId>
            <artifactId>qdrant-java-client</artifactId>
            <version>${qdrant-java-client.version}</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

---

## 4. 核心模块实现

### 4.1 mallchat-ai-llm 模块

#### 4.1.1 模块职责

- 封装大语言模型调用
- 提供流式和非流式接口
- Token计数和管理
- 熔断降级处理
- 多提供商切换（Qwen/Llama/OpenAI/ChatGLM/Mock）

#### 4.1.2 核心类结构

```java
com.abin.mallchat.ai.llm/
├── config/
│   ├── LangChain4jConfig.java      # OpenAI配置
│   └── LLMConfig.java               # LLM参数配置
├── service/
│   ├── LLMService.java              # LLM服务接口
│   ├── LLMServiceFactory.java       # LLM服务工厂
│   └── impl/
│       ├── QwenLLMService.java      # Qwen via Ollama（推荐）
│       ├── LlamaLLMService.java     # Llama via Ollama
│       ├── OpenAILLMService.java    # OpenAI兼容
│       ├── ChatGLMLLMService.java   # 智谱AI
│       └── MockLLMService.java      # Mock模式
├── domain/
│   ├── LLMOptions.java              # LLM调用选项
│   └── LLMProvider.java             # LLM提供商枚举
└── exception/
    ├── LLMException.java
    └── LLMApiException.java
```

#### 4.1.3 LLMService 接口

```java
public interface LLMService {
    Flux<String> streamChat(String prompt, LLMOptions options);
    String chat(String prompt, LLMOptions options);
    Flux<String> streamChat(List<ChatMessage> messages, LLMOptions options);
    String chat(List<ChatMessage> messages, LLMOptions options);
    int countTokens(String text);
}
```

#### 4.1.4 QwenLLMService 核心实现

```java
@Slf4j
@Service
@Profile("!mock")
@ConditionalOnProperty(name = "langchain4j.llm.provider", havingValue = "qwen")
public class QwenLLMService implements LLMService {

    @Value("${ollama.base-url:http://localhost:11434}")
    private String baseUrl;

    @Value("${ollama.model-name:qwen2.5:14b}")
    private String modelName;

    private ChatLanguageModel chatModel;
    private StreamingChatLanguageModel streamingChatModel;

    @PostConstruct
    public void init() {
        this.chatModel = OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .timeout(timeout)
                .build();

        this.streamingChatModel = OllamaStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .timeout(timeout)
                .build();
    }

    @Override
    @CircuitBreaker(name = "llmService", fallbackMethod = "streamChatFallback")
    @Retryable(value = {LLMApiException.class}, maxAttempts = 3,
               backoff = @Backoff(delay = 1000, multiplier = 2))
    public Flux<String> streamChat(String prompt, LLMOptions options) {
        return Flux.create(sink -> {
            streamingChatModel.generate(prompt, new StreamingResponseHandler<AiMessage>() {
                @Override
                public void onNext(String token) {
                    if (token != null && !token.isEmpty()) {
                        sink.next(token);
                    }
                }
                @Override
                public void onComplete(Response<AiMessage> response) {
                    sink.complete();
                }
                @Override
                public void onError(Throwable error) {
                    sink.error(new LLMApiException("Qwen API call failed", error));
                }
            });
        }, FluxSink.OverflowStrategy.BUFFER);
    }
}
```

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
│   └── MilvusConnectionPool.java    # Milvus连接池
├── service/
│   ├── VectorService.java           # 向量服务接口
│   ├── EmbeddingService.java        # 向量化服务接口
│   └── impl/
│       ├── QdrantVectorService.java # Qdrant实现（默认）
│       ├── MilvusVectorService.java # Milvus实现
│       ├── MockVectorService.java   # Mock实现
│       ├── OllamaBgeEmbeddingService.java # BGE实现
│       ├── M3eEmbeddingService.java # M3E实现
│       ├── OpenAIEmbeddingService.java # OpenAI兼容
│       └── MockEmbeddingService.java # Mock实现
└── domain/
    └── SearchResult.java            # 检索结果
```

#### 4.2.3 VectorService 接口

```java
public interface VectorService {
    void storeVectors(Long documentId, List<DocumentChunk> chunks);
    List<SearchResult> search(float[] queryVector, int topK, Long documentId);
    void deleteVectors(Long documentId);
    boolean exists(Long documentId);
}
```

#### 4.2.4 QdrantVectorService 动态向量创建

```java
private void createCollectionIfNotExists() throws ExecutionException, InterruptedException {
    boolean exists = qdrantClient.collectionExistsAsync(collectionName).get();
    if (exists) return;

    Collections.CollectionOperationResponse response = qdrantClient.createCollectionAsync(
            collectionName,
            Collections.VectorParams.newBuilder()
                    .setDistance(Collections.Distance.Cosine)
                    .setOnDisk(true)      // 向量落盘，降低内存
                    .setDynamic(true)     // 动态向量，支持 1024/768 维
                    .build()
    ).get();
}
```

#### 4.2.5 OllamaBgeEmbeddingService 核心实现

```java
@Slf4j
@Service
@Profile("!mock")
@ConditionalOnProperty(name = "embedding.provider", havingValue = "bge", matchIfMissing = true)
public class OllamaBgeEmbeddingService implements EmbeddingService {

    @Value("${ollama.base-url:http://localhost:11434}")
    private String baseUrl;

    @Value("${ollama.embedding-model:bge-large-zh-v1.5}")
    private String modelName;

    private EmbeddingModel embeddingModel;

    @PostConstruct
    public void init() {
        this.embeddingModel = OllamaEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(timeout)
                .maxRetries(maxRetries)
                .build();
    }

    @Override
    public float[] generateEmbedding(String text) {
        Response<Embedding> response = embeddingModel.embed(text);
        return response.content().vector();  // 1024 维
    }

    @Override
    public List<float[]> generateEmbeddings(List<String> texts) {
        List<TextSegment> segments = texts.stream()
                .map(TextSegment::from)
                .collect(Collectors.toList());
        Response<List<Embedding>> response = embeddingModel.embedAll(segments);
        return response.content().stream()
                .map(Embedding::vector)
                .collect(Collectors.toList());
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

#### 4.3.2 RAGServiceImpl 核心实现

```java
@Slf4j
@Service
public class RAGServiceImpl implements RAGService {

    @Autowired private KnowledgeDocumentDao knowledgeDocumentDao;
    @Autowired private DocumentChunkDao documentChunkDao;
    @Autowired private AIConversationDao aiConversationDao;
    @Autowired private VectorService vectorService;
    @Autowired private EmbeddingService embeddingService;
    @Autowired private LLMService llmService;
    @Autowired private DocumentConfig documentConfig;
    @Autowired private DocumentIndexingProducer documentIndexingProducer;
    @Autowired private DegradationService degradationService;
    @Autowired private DocumentMetadataCache documentMetadataCache;
    @Autowired private IndexStatusCache indexStatusCache;
    @Autowired private QueryResultCache queryResultCache;

    @Override
    public Flux<String> ragQuery(RAGQueryRequest request) {
        long startTime = System.currentTimeMillis();

        // 1. 检查索引状态
        if (request.getDocumentId() != null) {
            String indexStatus = indexStatusCache.getIndexStatus(request.getDocumentId());
            if (!IndexStatus.COMPLETED.name().equals(indexStatus)) {
                return Flux.just(getIndexStatusMessage(indexStatus));
            }
        }

        try {
            // 2. 尝试缓存
            List<SearchResult> searchResults = queryResultCache.getQueryResult(
                    request.getQuestion(), request.getDocumentId(), request.getTopK());

            if (searchResults == null) {
                // 3. 生成问题向量
                float[] queryVector = embeddingService.generateEmbedding(request.getQuestion());
                // 4. 向量检索
                searchResults = vectorService.search(queryVector, request.getTopK(), request.getDocumentId());
                // 5. 缓存结果
                if (!searchResults.isEmpty()) {
                    queryResultCache.cacheQueryResult(
                            request.getQuestion(), request.getDocumentId(), request.getTopK(), searchResults);
                }
            }

            // 6. 空结果降级
            if (searchResults.isEmpty()) {
                return fallbackToNormalQA(request, startTime);
            }

            // 7. 构造 Prompt 并调用 LLM
            String ragPrompt = buildRAGPrompt(request.getQuestion(), searchResults);
            LLMOptions options = LLMOptions.builder().temperature(0.7).maxTokens(2000).build();
            Flux<String> responseFlux = llmService.streamChat(ragPrompt, options);

            // 8. 保存对话历史
            StringBuilder fullResponse = new StringBuilder();
            return responseFlux
                    .doOnNext(fullResponse::append)
                    .doOnComplete(() -> {
                        long responseTime = System.currentTimeMillis() - startTime;
                        saveConversation(request, fullResponse.toString(), searchResults, responseTime);
                    });

        } catch (Exception e) {
            log.error("RAG查询异常，尝试降级处理", e);
            if (degradationService.shouldDegrade()) {
                return degradationService.degradedRAGQuery(request.getQuestion());
            }
            return Flux.just("抱歉，处理您的问题时发生错误，请稍后重试。");
        }
    }
}
```

#### 4.3.3 RAG Prompt 构建

```java
private String buildRAGPrompt(String question, List<SearchResult> searchResults) {
    StringBuilder prompt = new StringBuilder();
    prompt.append("你是一个专业的知识问答助手。请根据以下提供的知识库内容回答用户的问题。\n\n");
    prompt.append("回答要求：\n");
    prompt.append("1. 仅基于提供的知识库内容回答，不要编造信息\n");
    prompt.append("2. 如果知识库中没有相关信息，请明确告知用户\n");
    prompt.append("3. 回答要准确、简洁、易懂\n\n");
    prompt.append("知识库内容：\n---\n");

    List<SearchResult> uniqueResults = deduplicateSearchResults(searchResults);
    for (int i = 0; i < uniqueResults.size(); i++) {
        SearchResult result = uniqueResults.get(i);
        prompt.append(String.format("[片段 %d] (相似度: %.2f)\n", i + 1, result.getScore()));
        prompt.append(result.getContent()).append("\n\n");
    }
    prompt.append("---\n\n");
    prompt.append("用户问题：\n").append(question).append("\n\n请回答：");
    return prompt.toString();
}
```

#### 4.3.4 StreamController SSE 包装

```java
@PostMapping(value = "/rag/query", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> streamRAGQuery(@RequestBody RAGQueryRequest request) {
    String connectionId = generateConnectionId(request.getUserId());
    AtomicInteger chunkIndex = new AtomicInteger(0);

    Flux<String> contentFlux = ragService.ragQuery(request);

    Flux<ServerSentEvent<String>> contentEvents = contentFlux
            .map(content -> ServerSentEvent.<String>builder()
                    .event("message")
                    .data(toJson(StreamChunk.content(chunkIndex.getAndIncrement(), content)))
                    .build())
            .concatWith(Mono.fromCallable(() -> ServerSentEvent.<String>builder()
                    .event("done")
                    .data(toJson(StreamChunk.end(chunkIndex.get())))
                    .build()))
            .onErrorResume(error -> Mono.just(ServerSentEvent.<String>builder()
                    .event("error")
                    .data(toJson(StreamChunk.error(chunkIndex.get(), error.getMessage())))
                    .build()));

    // 心跳 + 超时检测
    Flux<ServerSentEvent<String>> heartbeatFlux = Flux.interval(Duration.ofSeconds(30))
            .map(tick -> ServerSentEvent.<String>builder()
                    .event("heartbeat")
                    .data(toJson(StreamChunk.heartbeat()))
                    .build());

    return Flux.merge(contentEvents, heartbeatFlux)
            .takeUntilOther(contentEvents.filter(e -> "done".equals(e.event()) || "error".equals(e.event())).next());
}
```

---

## 5. RAG 查询流程详解

```
用户查询
    ↓
1. 参数校验 + 索引状态检查
    ↓
2. 查询结果缓存（JetCache）
    ↓
[缓存命中] → 直接返回缓存的检索结果
    ↓
[缓存未命中]
    ↓
3. Embedding 向量化（Ollama bge / m3e）
    ↓
4. Qdrant 相似度检索（动态向量）
    ↓
5. 缓存检索结果
    ↓
6. 空结果判断
    ├── 空 → 降级到普通 QA（直接问 LLM）
    └── 非空 → 继续
    ↓
7. 构建 RAG Prompt
    ↓
8. LLM 流式生成（Qwen / Llama / OpenAI）
    ↓
9. SSE 逐字推送给前端
    ↓
10. 保存对话历史到 MySQL
```

---

## 6. 面试准备

### 6.1 高频问题

#### Q1: 你们项目AI模块怎么设计的？

**答**: 项目AI模块采用分层架构，自上而下分为接口层（Controller）、服务层（Service）、基础设施层（Vector/LLM/Embedding）。核心能力包括RAG知识问答、AI智能助手、文档处理和流式输出。技术选型上以本地开源方案为主：Ollama运行Qwen2.5-14B和bge-large-zh-v1.5，Qdrant作为向量库，LangChain4j 0.36.0做模型抽象，RocketMQ做异步索引，JetCache做多级缓存。

#### Q2: RAG是怎么实现的？

**答**: RAG流程分为五步：
1. 用户提问后先做参数校验和索引状态检查；
2. 查询缓存，未命中则生成问题向量；
3. 在Qdrant中做相似度检索Top-K；
4. 将检索结果构建成Prompt；
5. 调用LLM流式生成，通过SSE返回。
如果检索为空，会降级到普通QA模式。

#### Q3: 为什么选Qdrant不选Milvus？

**答**: 三个核心原因：
1. **部署简单**：Qdrant单容器即可运行，Milvus需要etcd、minio等多组件；
2. **动态向量**：我们支持bge（1024维）和m3e（768维）切换，Qdrant原生支持动态向量，Milvus需要固定维度；
3. **运维成本低**：更适合中小型项目和快速迭代。

#### Q4: Embedding怎么做的？

**答**: 通过Ollama本地部署bge-large-zh-v1.5，使用LangChain4j的OllamaEmbeddingModel封装。支持单条和批量生成，输出1024维向量。备选方案是m3e-base（768维），通过配置`embedding.provider`切换。Mock模式下使用MD5生成确定性伪随机向量。

#### Q5: 项目有什么可以优化的地方？

**答**: 按照路线图，下一步优化方向包括：
- RAG增强：混合检索（向量+BM25）、重排序、查询改写
- AI Agent：工具调用、ReAct循环、长期记忆
- 工程优化：调用链追踪、Token消耗看板、模型响应质量评估

### 6.2 核心技术点

| 技术点 | 关键实现 |
|--------|---------|
| 流式输出 | Reactor Flux + SSE |
| 动态向量 | Qdrant `setDynamic(true)` |
| 多轮对话 | `sessionId` + 历史消息加载 |
| 服务降级 | Resilience4j CircuitBreaker |
| 异步索引 | RocketMQ + Consumer |
| 缓存策略 | JetCache L1 Caffeine + L2 Redis |
| Mock模式 | `@Profile("mock")` + 接口多实现 |

---

*本文档为速查版，完整内容请参见 [mallchat-ai/docs/AI技术方案.md](../mallchat-ai/docs/AI技术方案.md)。*
*最后更新：2026-06-13。*
