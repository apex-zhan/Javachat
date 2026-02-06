# MallChat GPT 接入与 AI 模块架构详解

## 目录
1. [整体架构概览](#整体架构概览)
2. [技术栈演进](#技术栈演进)
3. [模块划分与职责](#模块划分与职责)
4. [核心实现细节](#核心实现细节)
5. [值得学习的设计模式](#值得学习的设计模式)

---

## 一、整体架构概览

MallChat 的 AI 功能采用了**分层模块化架构**，分为两个主要部分：

### 1.1 架构层次

```
┌─────────────────────────────────────────────────────────┐
│              mallchat-chat-server                       │
│  ┌──────────────────────────────────────────────────┐  │
│  │  com.abin.mallchat.common.chatai (旧版本)        │  │
│  │  - 简单的 GPT 聊天机器人                          │  │
│  │  - 基于 OkHttp 直接调用 OpenAI API               │  │
│  └──────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
                          ↓ 升级
┌─────────────────────────────────────────────────────────┐
│                  mallchat-ai (新版本)                   │
│  ┌──────────────────────────────────────────────────┐  │
│  │  mallchat-ai-common    (公共模块)                │  │
│  │  mallchat-ai-llm       (大语言模型)              │  │
│  │  mallchat-ai-vector    (向量存储)                │  │
│  │  mallchat-ai-rag       (RAG 检索增强)            │  │
│  │  mallchat-ai-assistant (智能助手)                │  │
│  └──────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```


---

## 二、技术栈演进

### 2.1 第一代：chatai 包（简单版本）

**位置**：`mallchat-chat-server/src/main/java/com/abin/mallchat/common/chatai`

**技术选型**：
- **HTTP 客户端**：OkHttp 3.x
- **API 调用**：直接调用 OpenAI API
- **上下文管理**：Redis 存储对话历史
- **限流控制**：自定义频率控制器

**核心特点**：
```java
// 直接使用 OkHttp 调用 OpenAI API
Response response = ChatGPTUtils.create(apiKey)
    .proxyUrl(proxyUrl)
    .model("gpt-3.5-turbo")
    .timeout(60)
    .maxTokens(2000)
    .message(context.getMsg())
    .send();
```

**优点**：
- 轻量级，依赖少
- 实现简单，易于理解
- 适合快速原型开发

**缺点**：
- 功能单一，只支持基础对话
- 缺乏流式输出支持
- 没有向量检索能力
- 难以扩展新功能

---

### 2.2 第二代：mallchat-ai 模块（企业级）

**位置**：`mallchat-ai/`

**技术选型演进**：

#### 阶段 1：Spring AI（失败）
- **尝试时间**：2025-01 初期
- **失败原因**：
  - Spring AI 需要 Spring Boot 3.x + Java 17+
  - 项目使用 Spring Boot 2.6.7 + Java 8
  - 无法升级（生产环境限制）

#### 阶段 2：LangChain4j（成功）
- **迁移时间**：2025-01-05
- **选择原因**：
  - ✅ 支持 Spring Boot 2.x + Java 8
  - ✅ 功能完善（流式输出、Token 计数、Embedding）
  - ✅ 社区活跃，文档完善
  - ✅ 易于集成和扩展

**核心依赖**：
```xml
<properties>
    <langchain4j.version>0.27.1</langchain4j.version>
    <milvus-sdk.version>2.3.4</milvus-sdk.version>
    <tika.version>2.9.1</tika.version>
</properties>
```


---

## 三、模块划分与职责

### 3.1 mallchat-ai-common（公共模块）

**职责**：提供 AI 功能的公共实体、DAO 和枚举

**核心内容**：
```
mallchat-ai-common/
├── domain/
│   ├── entity/
│   │   ├── KnowledgeDocument.java      # 知识文档实体
│   │   ├── DocumentChunk.java          # 文档分块实体
│   │   └── AIConversation.java         # AI 对话历史
│   └── enums/
│       ├── IndexStatus.java            # 索引状态枚举
│       └── ConversationType.java       # 对话类型枚举
└── dao/
    ├── KnowledgeDocumentDao.java
    ├── DocumentChunkDao.java
    └── AIConversationDao.java
```

**设计亮点**：
1. **实体设计**：完整的生命周期管理（创建时间、更新时间、索引状态）
2. **枚举设计**：使用枚举管理状态，避免魔法值
3. **DAO 层**：基于 MyBatis-Plus，提供批量操作能力

---

### 3.2 mallchat-ai-llm（大语言模型层）

**职责**：封装 LLM 调用，提供统一的 AI 对话接口

**核心类**：
```java
// 1. 配置类：LangChain4jConfig.java
@Configuration
public class LangChain4jConfig {
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
            .apiKey(apiKey)
            .baseUrl(baseUrl)
            .modelName(modelName)
            .temperature(temperature)
            .maxTokens(maxTokens)
            .timeout(timeout)
            .build();
    }
    
    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel() {
        return OpenAiStreamingChatModel.builder()
            .apiKey(apiKey)
            .baseUrl(baseUrl)
            .modelName(modelName)
            .build();
    }
}
```

**核心服务**：
```java
// 2. 服务实现：OpenAILLMService.java
@Service
public class OpenAILLMService implements LLMService {
    
    @Autowired
    private ChatLanguageModel chatLanguageModel;
    
    @Autowired
    private StreamingChatLanguageModel streamingChatLanguageModel;
    
    // 流式调用（重点）
    @Override
    public Flux<String> streamChat(String prompt, LLMOptions options) {
        return Flux.create(sink -> {
            streamingChatLanguageModel.generate(
                prompt,
                new StreamingResponseHandler<AiMessage>() {
                    @Override
                    public void onNext(String token) {
                        sink.next(token);  // 实时推送 token
                    }
                    
                    @Override
                    public void onComplete(Response<AiMessage> response) {
                        sink.complete();
                    }
                    
                    @Override
                    public void onError(Throwable error) {
                        sink.error(new LLMApiException("Stream chat failed", error));
                    }
                }
            );
        });
    }
    
    // Token 计数
    @Override
    public int countTokens(String text) {
        return tokenizer.estimateTokenCountInText(text);
    }
}
```

**设计亮点**：
1. **流式输出**：使用 Reactor Flux 实现 SSE（Server-Sent Events）
2. **熔断降级**：使用 `@CircuitBreaker` 注解实现服务降级
3. **重试机制**：使用 `@Retryable` 注解实现自动重试
4. **Token 计数**：精确估算 API 消耗


---

### 3.3 mallchat-ai-vector（向量存储层）

**职责**：管理向量数据的存储、检索和删除

**技术选型**：Milvus 向量数据库

**核心服务**：
```java
@Service
public class MilvusVectorService implements VectorService {
    
    // 1. 存储向量
    @Override
    public void storeVectors(Long documentId, List<DocumentChunk> chunks) {
        // 准备数据
        List<Long> documentIds = new ArrayList<>();
        List<Long> chunkIds = new ArrayList<>();
        List<List<Float>> vectors = new ArrayList<>();
        List<String> contents = new ArrayList<>();
        
        for (DocumentChunk chunk : chunks) {
            documentIds.add(documentId);
            chunkIds.add(chunk.getId());
            vectors.add(extractVectorFromMetadata(chunk.getMetadata()));
            contents.add(chunk.getContent());
        }
        
        // 批量插入
        InsertParam insertParam = InsertParam.newBuilder()
            .withCollectionName(collectionName)
            .withFields(fields)
            .build();
        
        milvusClient.insert(insertParam);
    }
    
    // 2. 相似度检索（核心）
    @Override
    @CircuitBreaker(name = "vectorStoreService")
    public List<SearchResult> search(float[] queryVector, int topK, Long documentId) {
        // 构建搜索参数
        SearchParam searchParam = SearchParam.newBuilder()
            .withCollectionName(collectionName)
            .withMetricType(MetricType.COSINE)  // 余弦相似度
            .withTopK(topK)
            .withVectors(Collections.singletonList(vectorList))
            .withVectorFieldName("vector")
            .withExpr(documentId != null ? "document_id == " + documentId : "")
            .build();
        
        // 执行搜索
        R<SearchResults> searchResp = milvusClient.search(searchParam);
        
        // 解析结果
        SearchResultsWrapper wrapper = new SearchResultsWrapper(searchResp.getData().getResults());
        List<SearchResult> results = new ArrayList<>();
        
        for (int i = 0; i < wrapper.getIDScore(0).size(); i++) {
            SearchResult result = SearchResult.builder()
                .documentId((Long) wrapper.getFieldData("document_id", 0).get(i))
                .chunkId((Long) wrapper.getFieldData("chunk_id", 0).get(i))
                .content((String) wrapper.getFieldData("content", 0).get(i))
                .score(wrapper.getIDScore(0).get(i).getScore())
                .build();
            results.add(result);
        }
        
        return results;
    }
    
    // 3. 幂等删除（重点）
    @Override
    public void deleteVectors(Long documentId) {
        // 先检查是否存在
        boolean exists = exists(documentId);
        if (!exists) {
            log.info("No vectors found, skipping deletion (idempotent)");
            return;  // 幂等性保证
        }
        
        // 执行删除
        String expr = "document_id == " + documentId;
        DeleteParam deleteParam = DeleteParam.newBuilder()
            .withCollectionName(collectionName)
            .withExpr(expr)
            .build();
        
        milvusClient.delete(deleteParam);
    }
}
```

**Embedding 服务**：
```java
@Service
public class OpenAIEmbeddingService implements EmbeddingService {
    
    @Autowired
    private EmbeddingModel embeddingModel;
    
    @Override
    public float[] generateEmbedding(String text) {
        // 调用 OpenAI Embedding API
        Response<Embedding> response = embeddingModel.embed(text);
        
        // 提取向量
        List<Float> vectorList = response.content().vector();
        
        // 转换为 float[]
        float[] vector = new float[vectorList.size()];
        for (int i = 0; i < vectorList.size(); i++) {
            vector[i] = vectorList.get(i);
        }
        
        return vector;
    }
}
```

**设计亮点**：
1. **Collection 自动创建**：启动时自动创建 Milvus Collection 和索引
2. **幂等性保证**：删除操作支持幂等，多次调用结果一致
3. **熔断降级**：向量检索失败时返回空结果，不影响主流程
4. **批量操作**：支持批量插入向量，提高性能


---

### 3.4 mallchat-ai-rag（RAG 检索增强层）

**职责**：实现 RAG（Retrieval-Augmented Generation）知识问答

**核心流程**：
```
用户提问 → 向量检索 → 构造 Prompt → LLM 生成 → 流式返回
```

**核心服务**：
```java
@Service
public class RAGServiceImpl implements RAGService {
    
    @Autowired
    private VectorService vectorService;
    
    @Autowired
    private EmbeddingService embeddingService;
    
    @Autowired
    private LLMService llmService;
    
    @Override
    public Flux<String> ragQuery(RAGQueryRequest request) {
        // 1. 检查索引状态（使用缓存）
        String indexStatus = indexStatusCache.getIndexStatus(request.getDocumentId());
        if (!IndexStatus.COMPLETED.name().equals(indexStatus)) {
            return Flux.just("文档正在索引中，请稍后再试。");
        }
        
        // 2. 生成问题向量
        float[] queryVector = embeddingService.generateEmbedding(request.getQuestion());
        
        // 3. 向量检索（Top-K）
        List<SearchResult> searchResults = vectorService.search(
            queryVector, 
            request.getTopK(), 
            request.getDocumentId()
        );
        
        // 4. 检查检索结果
        if (searchResults.isEmpty()) {
            log.warn("未找到相关知识片段，降级到普通问答");
            return fallbackToNormalQA(request);
        }
        
        // 5. 构造 RAG Prompt
        String ragPrompt = buildRAGPrompt(request.getQuestion(), searchResults);
        
        // 6. 调用 LLM 流式生成
        LLMOptions options = LLMOptions.builder()
            .temperature(0.7)
            .maxTokens(2000)
            .build();
        
        Flux<String> responseFlux = llmService.streamChat(ragPrompt, options);
        
        // 7. 保存对话历史
        StringBuilder fullResponse = new StringBuilder();
        return responseFlux
            .doOnNext(chunk -> fullResponse.append(chunk))
            .doOnComplete(() -> {
                saveConversation(request, fullResponse.toString(), searchResults);
            });
    }
    
    // 构造 RAG Prompt（核心）
    private String buildRAGPrompt(String question, List<SearchResult> searchResults) {
        StringBuilder prompt = new StringBuilder();
        
        // 系统指令
        prompt.append("你是一个专业的知识问答助手。请根据以下提供的知识库内容回答用户的问题。\n\n");
        prompt.append("回答要求：\n");
        prompt.append("1. 仅基于提供的知识库内容回答，不要编造信息\n");
        prompt.append("2. 如果知识库中没有相关信息，请明确告知用户\n");
        prompt.append("3. 回答要准确、简洁、易懂\n\n");
        
        // 检索上下文
        prompt.append("知识库内容：\n");
        prompt.append("---\n");
        for (int i = 0; i < searchResults.size(); i++) {
            SearchResult result = searchResults.get(i);
            prompt.append(String.format("[片段 %d] (相似度: %.2f)\n", i + 1, result.getScore()));
            prompt.append(result.getContent());
            prompt.append("\n\n");
        }
        prompt.append("---\n\n");
        
        // 用户问题
        prompt.append("用户问题：\n");
        prompt.append(question);
        prompt.append("\n\n请回答：");
        
        return prompt.toString();
    }
}
```

**文档处理流程**：
```java
// 1. 文档上传
@Override
public DocumentUploadResponse uploadDocument(DocumentUploadRequest request) {
    // 验证文档格式和大小
    validateDocument(request.getFile());
    
    // 保存文档到本地或 OSS
    String filePath = saveDocument(request.getFile());
    
    // 创建文档记录
    KnowledgeDocument document = new KnowledgeDocument();
    document.setIndexStatus(IndexStatus.PENDING.name());
    knowledgeDocumentDao.save(document);
    
    // 触发异步索引任务（RocketMQ）
    DocumentIndexingMessage message = DocumentIndexingMessage.builder()
        .documentId(document.getId())
        .filePath(filePath)
        .build();
    documentIndexingProducer.sendIndexingTask(message);
    
    return DocumentUploadResponse.builder()
        .documentId(document.getId())
        .message("文档上传成功，正在等待索引处理")
        .build();
}

// 2. 异步索引处理（消费者）
@RocketMQMessageListener(
    topic = "MALLCHAT_AI_DOCUMENT_INDEXING",
    consumerGroup = "mallchat-ai-indexing-consumer"
)
public class DocumentIndexingConsumer implements RocketMQListener<DocumentIndexingMessage> {
    
    @Override
    public void onMessage(DocumentIndexingMessage message) {
        try {
            // 更新状态为索引中
            updateIndexStatus(message.getDocumentId(), IndexStatus.INDEXING);
            
            // 解析文档（使用 Apache Tika）
            String content = tikaDocumentProcessingService.extractText(message.getFilePath());
            
            // 文档分块
            List<DocumentChunk> chunks = chunkStrategy.chunk(content);
            
            // 生成向量
            for (DocumentChunk chunk : chunks) {
                float[] vector = embeddingService.generateEmbedding(chunk.getContent());
                chunk.setMetadata(buildMetadata(vector));
            }
            
            // 保存分块到数据库
            documentChunkDao.saveBatch(chunks);
            
            // 存储向量到 Milvus
            vectorService.storeVectors(message.getDocumentId(), chunks);
            
            // 更新状态为完成
            updateIndexStatus(message.getDocumentId(), IndexStatus.COMPLETED);
            
        } catch (Exception e) {
            log.error("文档索引失败", e);
            updateIndexStatus(message.getDocumentId(), IndexStatus.FAILED);
        }
    }
}
```

**设计亮点**：
1. **异步索引**：使用 RocketMQ 实现异步文档处理，不阻塞用户上传
2. **分块策略**：支持多种分块策略（固定大小、语义分块）
3. **缓存优化**：索引状态、查询结果使用 Redis 缓存
4. **降级策略**：向量检索失败时降级到普通问答
5. **幂等更新**：文档更新时先幂等删除旧向量，再插入新向量


---

### 3.5 com.abin.mallchat.common.chatai（旧版聊天机器人）

**职责**：提供简单的 GPT 聊天机器人功能

**核心设计模式**：策略模式 + 工厂模式

**类图**：
```
AbstractChatAIHandler (抽象处理器)
    ├── GPTChatAIHandler (GPT 处理器)
    └── ChatGLM2Handler (ChatGLM 处理器)

ChatAIHandlerFactory (工厂类)
    └── 根据 AI 用户 ID 获取对应的处理器
```

**核心实现**：
```java
// 1. 抽象处理器
public abstract class AbstractChatAIHandler {
    
    @Autowired
    @Qualifier(ThreadPoolConfig.AICHAT_EXECUTOR)
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    
    @Autowired
    protected ChatService chatService;
    
    // 注册到工厂
    @PostConstruct
    protected void init() {
        if (isUse()) {
            ChatAIHandlerFactory.register(getChatAIUserId(), this);
        }
    }
    
    // 模板方法
    public void chat(Message message) {
        if (!supports(message)) {
            return;
        }
        
        // 异步处理
        threadPoolTaskExecutor.execute(() -> {
            String text = doChat(message);
            if (StringUtils.isNotBlank(text)) {
                answerMsg(text, message);
            }
        });
    }
    
    // 子类实现
    protected abstract boolean isUse();
    protected abstract Long getChatAIUserId();
    protected abstract boolean supports(Message message);
    protected abstract String doChat(Message message);
    
    // 回复消息（支持长文本分段）
    protected void answerMsg(String text, Message replyMessage) {
        UserInfoResp userInfo = userService.getUserInfo(replyMessage.getFromUid());
        text = "@" + userInfo.getName() + " " + text;
        
        if (text.length() < 800) {
            save(text, replyMessage);
        } else {
            // 分段发送
            int maxLen = 800;
            int count = (text.length() + maxLen - 1) / maxLen;
            for (int i = 0; i < count; i++) {
                int start = i * maxLen;
                int end = Math.min(start + maxLen, text.length());
                save(text.substring(start, end), replyMessage);
            }
        }
    }
}

// 2. GPT 处理器实现
@Component
public class GPTChatAIHandler extends AbstractChatAIHandler {
    
    @Autowired
    private ChatGPTProperties chatGPTProperties;
    
    @Override
    protected String doChat(Message message) {
        Long uid = message.getFromUid();
        
        try {
            // 频率控制
            FrequencyControlDTO frequencyControlDTO = new FrequencyControlDTO();
            frequencyControlDTO.setKey("GPTChatAIHandler:" + uid);
            frequencyControlDTO.setCount(chatGPTProperties.getLimit());
            frequencyControlDTO.setUnit(TimeUnit.HOURS);
            
            return FrequencyControlUtil.executeWithFrequencyControl(
                TOTAL_COUNT_WITH_IN_FIX_TIME_FREQUENCY_CONTROLLER,
                frequencyControlDTO,
                () -> sendRequestToGPT(message)
            );
        } catch (FrequencyControlException e) {
            return "亲爱的,你今天找我聊了" + chatGPTProperties.getLimit() + "次了~人家累了~明天见";
        }
    }
    
    private String sendRequestToGPT(Message message) {
        // 构建上下文
        ChatGPTContext context = buildContext(message);
        
        // 裁剪上下文（控制 Token 数量）
        context = tailorContext(context);
        
        // 调用 GPT API
        Response response = ChatGPTUtils.create(chatGPTProperties.getKey())
            .proxyUrl(chatGPTProperties.getProxyUrl())
            .model(chatGPTProperties.getModelName())
            .timeout(chatGPTProperties.getTimeout())
            .maxTokens(chatGPTProperties.getMaxTokens())
            .message(context.getMsg())
            .send();
        
        String text = ChatGPTUtils.parseText(response);
        
        // 保存上下文到 Redis
        context.addMsg(ChatGPTMsgBuilder.assistantMsg(text));
        saveContext(context);
        
        return text;
    }
    
    // 上下文裁剪（递归）
    private ChatGPTContext tailorContext(ChatGPTContext context) {
        List<ChatGPTMsg> msg = context.getMsg();
        Integer tokenCount = ChatGPTUtils.countTokens(msg);
        
        if (tokenCount < (chatGPTProperties.getMaxTokens() - 500)) {
            return context;
        }
        
        // 删除最早的消息（保留系统消息）
        msg.remove(1);
        return tailorContext(context);
    }
    
    @Override
    protected boolean supports(Message message) {
        // 检查是否 @ 了 AI 用户
        MessageExtra extra = message.getExtra();
        if (extra == null || CollectionUtils.isEmpty(extra.getAtUidList())) {
            return false;
        }
        
        return extra.getAtUidList().contains(chatGPTProperties.getAIUserId())
            && StringUtils.contains(message.getContent(), "@" + AI_NAME);
    }
}

// 3. 工厂类
public class ChatAIHandlerFactory {
    private static final Map<Long, AbstractChatAIHandler> CHATAI_ID_MAP = new ConcurrentHashMap<>();
    
    public static void register(Long aIUserId, AbstractChatAIHandler chatAIHandler) {
        CHATAI_ID_MAP.put(aIUserId, chatAIHandler);
    }
    
    public static AbstractChatAIHandler getChatAIHandlerById(List<Long> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return null;
        }
        
        for (Long userId : userIds) {
            AbstractChatAIHandler chatAIHandler = CHATAI_ID_MAP.get(userId);
            if (chatAIHandler != null) {
                return chatAIHandler;
            }
        }
        return null;
    }
}
```

**设计亮点**：
1. **策略模式**：不同的 AI 模型使用不同的处理器
2. **工厂模式**：根据 @ 的用户 ID 自动选择处理器
3. **模板方法**：定义统一的处理流程，子类实现具体逻辑
4. **异步处理**：使用线程池异步处理 AI 请求，不阻塞主线程
5. **频率控制**：使用自定义频率控制器限制用户调用次数
6. **上下文管理**：使用 Redis 存储对话上下文，支持多轮对话
7. **长文本分段**：超过 800 字符自动分段发送


---

## 四、核心实现细节

### 4.1 流式输出实现（SSE）

**技术选型**：Reactor Flux + StreamingResponseHandler

**实现原理**：
```java
// 1. LLM 层：使用 Flux.create 创建流
public Flux<String> streamChat(String prompt, LLMOptions options) {
    return Flux.create(sink -> {
        streamingChatLanguageModel.generate(
            prompt,
            new StreamingResponseHandler<AiMessage>() {
                @Override
                public void onNext(String token) {
                    // 每收到一个 token 就推送
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
                    sink.error(new LLMApiException("Stream chat failed", error));
                }
            }
        );
    });
}

// 2. Controller 层：返回 SSE 流
@GetMapping(value = "/rag/query", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> ragQuery(@RequestBody RAGQueryRequest request) {
    return ragService.ragQuery(request)
        .map(chunk -> ServerSentEvent.<String>builder()
            .data(chunk)
            .build()
        )
        .concatWith(Flux.just(
            ServerSentEvent.<String>builder()
                .event("complete")
                .data("[DONE]")
                .build()
        ));
}

// 3. 前端：使用 EventSource 接收流
const eventSource = new EventSource('/api/rag/query');

eventSource.onmessage = (event) => {
    if (event.data === '[DONE]') {
        eventSource.close();
        return;
    }
    
    // 逐字显示
    displayText += event.data;
    updateUI(displayText);
};
```

**优势**：
- 用户体验好：逐字显示，类似 ChatGPT
- 资源占用低：不需要等待完整响应
- 支持长文本：避免超时问题

---

### 4.2 向量检索优化

**1. 索引策略**：
```yaml
milvus:
  collection:
    index-type: IVF_FLAT      # 倒排文件索引
    metric-type: COSINE        # 余弦相似度
    index-params:
      nlist: 1024              # 聚类中心数量
    search-params:
      nprobe: 10               # 搜索的聚类数量
```

**2. 检索流程**：
```
用户问题 
  ↓
生成向量 (text-embedding-ada-002, 1536维)
  ↓
Milvus 检索 (COSINE 相似度, Top-K=5)
  ↓
过滤低分结果 (相似度阈值 0.7)
  ↓
去重 (基于内容相似度)
  ↓
返回最相关的片段
```

**3. 性能优化**：
- **批量插入**：一次插入多个向量，减少网络开销
- **缓存查询结果**：热门查询结果缓存 5 分钟
- **索引状态缓存**：避免频繁查询数据库
- **连接池管理**：复用 Milvus 连接

---

### 4.3 文档分块策略

**策略接口**：
```java
public interface ChunkStrategy {
    List<DocumentChunk> chunk(String content, ChunkOptions options);
}
```

**实现策略**：

**1. 固定大小分块**：
```java
@Component
public class FixedSizeChunkStrategy implements ChunkStrategy {
    
    @Override
    public List<DocumentChunk> chunk(String content, ChunkOptions options) {
        int chunkSize = options.getChunkSize();      // 500 tokens
        int overlap = options.getChunkOverlap();     // 50 tokens
        
        List<DocumentChunk> chunks = new ArrayList<>();
        int start = 0;
        int index = 0;
        
        while (start < content.length()) {
            int end = Math.min(start + chunkSize, content.length());
            String chunkContent = content.substring(start, end);
            
            DocumentChunk chunk = DocumentChunk.builder()
                .chunkIndex(index++)
                .content(chunkContent)
                .tokenCount(tokenizer.countTokens(chunkContent))
                .build();
            
            chunks.add(chunk);
            
            // 重叠部分
            start = end - overlap;
        }
        
        return chunks;
    }
}
```

**2. 语义分块**（未来扩展）：
- 按段落分块
- 按句子分块
- 基于语义相似度分块

**分块参数**：
```yaml
document:
  processing:
    chunking:
      default-chunk-size: 500    # 默认分块大小
      chunk-overlap: 50          # 重叠大小
      min-chunk-size: 100        # 最小分块
      max-chunk-size: 1000       # 最大分块
```

**为什么需要重叠**：
- 避免语义被截断
- 提高检索召回率
- 保持上下文连贯性


---

### 4.4 缓存设计

**三级缓存架构**：

```java
// 1. 文档元数据缓存
@Component
public class DocumentMetadataCache {
    
    @Cacheable(value = "document:metadata", key = "#documentId")
    public KnowledgeDocument getDocumentMetadata(Long documentId) {
        return knowledgeDocumentDao.getById(documentId);
    }
    
    @CacheEvict(value = "document:metadata", key = "#documentId")
    public void invalidateDocumentMetadata(Long documentId) {
        // 缓存失效
    }
}

// 2. 索引状态缓存
@Component
public class IndexStatusCache {
    
    @Cacheable(value = "document:index-status", key = "#documentId", ttl = 300)
    public String getIndexStatus(Long documentId) {
        KnowledgeDocument document = knowledgeDocumentDao.getById(documentId);
        return document != null ? document.getIndexStatus() : IndexStatus.FAILED.name();
    }
    
    @CacheEvict(value = "document:index-status", key = "#documentId")
    public void invalidateIndexStatus(Long documentId) {
        // 缓存失效
    }
}

// 3. 查询结果缓存
@Component
public class QueryResultCache {
    
    public List<SearchResult> getQueryResult(String question, Long documentId, int topK) {
        String cacheKey = buildCacheKey(question, documentId, topK);
        return RedisUtils.get(cacheKey, new TypeReference<List<SearchResult>>() {});
    }
    
    public void cacheQueryResult(String question, Long documentId, int topK, 
                                  List<SearchResult> results) {
        String cacheKey = buildCacheKey(question, documentId, topK);
        RedisUtils.set(cacheKey, results, 300, TimeUnit.SECONDS);  // 5分钟
    }
    
    public void invalidateByDocumentId(Long documentId) {
        // 删除该文档的所有查询缓存
        String pattern = "query:result:*:doc:" + documentId + ":*";
        RedisUtils.deleteByPattern(pattern);
    }
    
    private String buildCacheKey(String question, Long documentId, int topK) {
        String questionHash = DigestUtils.md5Hex(question);
        return String.format("query:result:%s:doc:%s:topk:%d", 
                             questionHash, documentId, topK);
    }
}
```

**缓存策略**：
| 缓存类型 | TTL | 失效时机 |
|---------|-----|---------|
| 文档元数据 | 1小时 | 文档更新/删除 |
| 索引状态 | 5分钟 | 索引状态变更 |
| 查询结果 | 5分钟 | 文档更新/删除 |

---

### 4.5 异步处理与消息队列

**RocketMQ 集成**：

```java
// 1. 生产者：发送索引任务
@Service
public class DocumentIndexingProducerImpl implements DocumentIndexingProducer {
    
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    
    @Override
    public void sendIndexingTask(DocumentIndexingMessage message) {
        try {
            SendResult sendResult = rocketMQTemplate.syncSend(
                "MALLCHAT_AI_DOCUMENT_INDEXING",
                MessageBuilder.withPayload(message).build(),
                3000  // 超时时间
            );
            
            if (sendResult.getSendStatus() == SendStatus.SEND_OK) {
                log.info("索引任务发送成功，文档ID：{}", message.getDocumentId());
            } else {
                log.error("索引任务发送失败，文档ID：{}", message.getDocumentId());
            }
        } catch (Exception e) {
            log.error("发送索引任务异常", e);
            throw new RuntimeException("发送索引任务失败", e);
        }
    }
}

// 2. 消费者：���理索引任务
@Component
@RocketMQMessageListener(
    topic = "MALLCHAT_AI_DOCUMENT_INDEXING",
    consumerGroup = "mallchat-ai-indexing-consumer",
    consumeMode = ConsumeMode.ORDERLY  // 顺序消费
)
public class DocumentIndexingConsumer implements RocketMQListener<DocumentIndexingMessage> {
    
    @Autowired
    private DocumentProcessingService documentProcessingService;
    
    @Autowired
    private VectorService vectorService;
    
    @Override
    public void onMessage(DocumentIndexingMessage message) {
        Long documentId = message.getDocumentId();
        
        try {
            log.info("开始处理文档索引，文档ID：{}", documentId);
            
            // 1. 更新状态为索引中
            updateIndexStatus(documentId, IndexStatus.INDEXING);
            
            // 2. 解析文档
            String content = documentProcessingService.extractText(message.getFilePath());
            
            // 3. 文档分块
            List<DocumentChunk> chunks = documentProcessingService.chunkDocument(
                documentId, 
                content
            );
            
            // 4. 生成向量并保存
            documentProcessingService.generateAndStoreVectors(documentId, chunks);
            
            // 5. 更新状态为完成
            updateIndexStatus(documentId, IndexStatus.COMPLETED);
            
            log.info("文档索引完成，文档ID：{}，分块数：{}", documentId, chunks.size());
            
        } catch (Exception e) {
            log.error("文档索引失败，文档ID：{}", documentId, e);
            
            // 更新状态为失败
            updateIndexStatus(documentId, IndexStatus.FAILED, e.getMessage());
            
            // 重试逻辑
            if (message.getRetryCount() < 3) {
                message.setRetryCount(message.getRetryCount() + 1);
                // 延迟重试
                rocketMQTemplate.syncSendDelayLevel(
                    "MALLCHAT_AI_DOCUMENT_INDEXING",
                    message,
                    3  // 延迟级别（10秒）
                );
            }
        }
    }
}
```

**消息设计**：
```java
@Data
@Builder
public class DocumentIndexingMessage implements Serializable {
    private Long documentId;
    private String title;
    private String filePath;
    private String documentType;
    private Integer retryCount;
    private LocalDateTime createTime;
}
```

**优势**：
- **解耦**：上传和索引分离
- **异步**：不阻塞用户操作
- **可靠**：消息持久化，支持重试
- **可扩展**：支持水平扩展消费者


---

### 4.6 容错与降级

**1. 熔断器配置**：
```yaml
resilience4j:
  circuitbreaker:
    instances:
      llmService:
        failure-rate-threshold: 50              # 失败率阈值 50%
        slow-call-rate-threshold: 50            # 慢调用率阈值 50%
        slow-call-duration-threshold: 5000      # 慢调用时间阈值 5秒
        sliding-window-size: 10                 # 滑动窗口大小
        minimum-number-of-calls: 5              # 最小调用次数
        wait-duration-in-open-state: 60000      # 熔断器打开后等待时间 60秒
      
      vectorStoreService:
        failure-rate-threshold: 50
        sliding-window-size: 10
        wait-duration-in-open-state: 30000
```

**2. 降级服务**：
```java
@Service
public class DegradationService {
    
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private static final int FAILURE_THRESHOLD = 5;
    
    // 判断是否需要降级
    public boolean shouldDegrade() {
        return failureCount.get() >= FAILURE_THRESHOLD;
    }
    
    // 记录失败
    public void recordFailure() {
        int count = failureCount.incrementAndGet();
        log.warn("服务失败次数：{}", count);
        
        if (count >= FAILURE_THRESHOLD) {
            log.error("服务失败次数达到阈值，触发降级");
        }
    }
    
    // 重置失败计数
    public void resetFailureCount() {
        failureCount.set(0);
    }
    
    // 降级的 RAG 查询（不使用向量检索）
    public Flux<String> degradedRAGQuery(String question) {
        log.info("使用降级策略处理查询：{}", question);
        
        // 直接调用 LLM，不使用向量检索
        String prompt = "请回答以下问题：\n" + question;
        
        LLMOptions options = LLMOptions.builder()
            .temperature(0.7)
            .maxTokens(1000)
            .build();
        
        return llmService.streamChat(prompt, options);
    }
}
```

**3. 重试机制**：
```java
@Service
public class OpenAILLMService implements LLMService {
    
    @Override
    @Retryable(
        value = {LLMApiException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public String chat(String prompt, LLMOptions options) {
        // 调用 LLM API
        return chatLanguageModel.generate(prompt);
    }
    
    @Recover
    public String recover(LLMApiException e, String prompt, LLMOptions options) {
        log.error("LLM 调用失败，已达最大重试次数", e);
        return "抱歉，AI 服务暂时不可用，请稍后再试。";
    }
}
```

**降级策略**：
| 场景 | 降级方案 |
|------|---------|
| LLM 不可用 | 返回友好提示 |
| 向量库不可用 | 降级到普通问答 |
| 文档解析失败 | 标记失败，支持重试 |
| 索引超时 | 异步处理，不阻塞用户 |

---

### 4.7 限流设计

**1. 全局限流**：
```java
@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, 
                             Object handler) throws Exception {
        Long userId = getCurrentUserId(request);
        
        // 检查限流
        String key = "rate:limit:rag:query:" + userId;
        Long count = redisTemplate.opsForValue().increment(key);
        
        if (count == 1) {
            // 第一次访问，设置过期时间
            redisTemplate.expire(key, 1, TimeUnit.MINUTES);
        }
        
        if (count > 10) {
            // 超过限流阈值
            response.setStatus(429);
            response.getWriter().write("请求过于频繁，请稍后再试");
            return false;
        }
        
        return true;
    }
}
```

**2. 分级限流**：
```yaml
ai:
  rate-limit:
    enabled: true
    rag-query:
      max-requests-per-minute: 10
      max-requests-per-hour: 100
      max-requests-per-day: 500
    document-upload:
      max-uploads-per-hour: 10
      max-uploads-per-day: 50
```

**3. 用户级限流**（旧版 chatai）：
```java
@Override
protected String doChat(Message message) {
    Long uid = message.getFromUid();
    
    try {
        FrequencyControlDTO frequencyControlDTO = new FrequencyControlDTO();
        frequencyControlDTO.setKey("GPTChatAIHandler:" + uid);
        frequencyControlDTO.setCount(chatGPTProperties.getLimit());  // 每小时限制次数
        frequencyControlDTO.setUnit(TimeUnit.HOURS);
        
        return FrequencyControlUtil.executeWithFrequencyControl(
            TOTAL_COUNT_WITH_IN_FIX_TIME_FREQUENCY_CONTROLLER,
            frequencyControlDTO,
            () -> sendRequestToGPT(message)
        );
    } catch (FrequencyControlException e) {
        return "亲爱的,你今天找我聊了" + chatGPTProperties.getLimit() + "次了~人家累了~明天见";
    }
}
```


---

## 五、值得学习的设计模式与最佳实践

### 5.1 设计模式应用

#### 1. 策略模式（Strategy Pattern）
**应用场景**：不同的 AI 模型处理器

```java
// 策略接口
public abstract class AbstractChatAIHandler {
    protected abstract String doChat(Message message);
}

// 具体策略
public class GPTChatAIHandler extends AbstractChatAIHandler {
    @Override
    protected String doChat(Message message) {
        // GPT 处理逻辑
    }
}

public class ChatGLM2Handler extends AbstractChatAIHandler {
    @Override
    protected String doChat(Message message) {
        // ChatGLM 处理逻辑
    }
}
```

**优势**：
- 易于扩展新的 AI 模型
- 符合开闭原则
- 运行时动态选择策略

---

#### 2. 工厂模式（Factory Pattern）
**应用场景**：根据用户 ID 获取对应的 AI 处理器

```java
public class ChatAIHandlerFactory {
    private static final Map<Long, AbstractChatAIHandler> CHATAI_ID_MAP = new ConcurrentHashMap<>();
    
    public static void register(Long aIUserId, AbstractChatAIHandler chatAIHandler) {
        CHATAI_ID_MAP.put(aIUserId, chatAIHandler);
    }
    
    public static AbstractChatAIHandler getChatAIHandlerById(List<Long> userIds) {
        for (Long userId : userIds) {
            AbstractChatAIHandler handler = CHATAI_ID_MAP.get(userId);
            if (handler != null) {
                return handler;
            }
        }
        return null;
    }
}
```

**优势**：
- 解耦对象创建和使用
- 集中管理对象实例
- 支持运行时注册

---

#### 3. 模板方法模式（Template Method Pattern）
**应用场景**：定义 AI 处理的统一流程

```java
public abstract class AbstractChatAIHandler {
    
    // 模板方法
    public void chat(Message message) {
        // 1. 检查是否支持
        if (!supports(message)) {
            return;
        }
        
        // 2. 异步处理
        threadPoolTaskExecutor.execute(() -> {
            // 3. 执行聊天（子类实现）
            String text = doChat(message);
            
            // 4. 回复消息
            if (StringUtils.isNotBlank(text)) {
                answerMsg(text, message);
            }
        });
    }
    
    // 抽象方法（子类实现）
    protected abstract boolean supports(Message message);
    protected abstract String doChat(Message message);
}
```

**优势**：
- 定义算法骨架
- 子类实现具体步骤
- 避免代码重复

---

#### 4. 观察者模式（Observer Pattern）
**应用场景**：文档索引状态变更通知

```java
// 事件发布
@Component
public class DocumentIndexingEventPublisher {
    
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    
    public void publishIndexingCompleted(Long documentId) {
        DocumentIndexingCompletedEvent event = new DocumentIndexingCompletedEvent(this, documentId);
        eventPublisher.publishEvent(event);
    }
}

// 事件监听
@Component
public class DocumentIndexingEventListener {
    
    @EventListener
    public void onIndexingCompleted(DocumentIndexingCompletedEvent event) {
        Long documentId = event.getDocumentId();
        
        // 清除缓存
        indexStatusCache.invalidateIndexStatus(documentId);
        
        // 发送通知
        notificationService.notifyUser(documentId, "文档索引完成");
    }
}
```

---

#### 5. 责任链模式（Chain of Responsibility）
**应用场景**：文档处理流水线

```java
public interface DocumentProcessor {
    void process(DocumentContext context);
    void setNext(DocumentProcessor next);
}

// 文档解析处理器
public class DocumentParsingProcessor implements DocumentProcessor {
    private DocumentProcessor next;
    
    @Override
    public void process(DocumentContext context) {
        // 解析文档
        String content = tikaService.extractText(context.getFilePath());
        context.setContent(content);
        
        // 传递给下一个处理器
        if (next != null) {
            next.process(context);
        }
    }
    
    @Override
    public void setNext(DocumentProcessor next) {
        this.next = next;
    }
}

// 文档分块处理器
public class DocumentChunkingProcessor implements DocumentProcessor {
    private DocumentProcessor next;
    
    @Override
    public void process(DocumentContext context) {
        // 文档分块
        List<DocumentChunk> chunks = chunkStrategy.chunk(context.getContent());
        context.setChunks(chunks);
        
        if (next != null) {
            next.process(context);
        }
    }
}

// 向量生成处理器
public class VectorGenerationProcessor implements DocumentProcessor {
    @Override
    public void process(DocumentContext context) {
        // 生成向量
        for (DocumentChunk chunk : context.getChunks()) {
            float[] vector = embeddingService.generateEmbedding(chunk.getContent());
            chunk.setVector(vector);
        }
    }
}

// 使用责任链
DocumentProcessor chain = new DocumentParsingProcessor();
chain.setNext(new DocumentChunkingProcessor());
chain.setNext(new VectorGenerationProcessor());

chain.process(documentContext);
```

---

### 5.2 最佳实践

#### 1. 幂等性设计
**场景**：向量删除操作

```java
@Override
public void deleteVectors(Long documentId) {
    // 先检查是否存在
    boolean exists = exists(documentId);
    
    if (!exists) {
        log.info("No vectors found, skipping deletion (idempotent)");
        return;  // 幂等性保证：不存在时直接返回成功
    }
    
    // 执行删除
    milvusClient.delete(deleteParam);
    
    // 验证删除结果
    boolean stillExists = exists(documentId);
    if (stillExists) {
        log.warn("Vectors still exist after deletion, may need retry");
    }
}
```

**关键点**：
- 操作前检查状态
- 多次调用结果一致
- 不抛出异常（除非系统错误）

---

#### 2. 异步处理
**场景**：文档索引

```java
// 同步接口：快速返回
@Override
public DocumentUploadResponse uploadDocument(DocumentUploadRequest request) {
    // 1. 保存文档
    KnowledgeDocument document = saveDocument(request);
    
    // 2. 发送异步任务
    documentIndexingProducer.sendIndexingTask(message);
    
    // 3. 立即返回
    return DocumentUploadResponse.builder()
        .documentId(document.getId())
        .message("文档上传成功，正在等待索引处理")
        .build();
}

// 异步处理：消费者处理
@RocketMQMessageListener(topic = "DOCUMENT_INDEXING")
public class DocumentIndexingConsumer implements RocketMQListener<DocumentIndexingMessage> {
    @Override
    public void onMessage(DocumentIndexingMessage message) {
        // 耗时的索引处理
        processDocumentIndexing(message);
    }
}
```

**优势**：
- 不阻塞用户操作
- 提高系统吞吐量
- 支持重试和容错

---

#### 3. 缓存策略
**多级缓存**：

```java
// L1: 本地缓存（Caffeine）
@Cacheable(value = "local:document", key = "#documentId")
public KnowledgeDocument getDocumentFromLocal(Long documentId) {
    return getDocumentFromRedis(documentId);
}

// L2: 分布式缓存（Redis）
public KnowledgeDocument getDocumentFromRedis(Long documentId) {
    String key = "document:metadata:" + documentId;
    KnowledgeDocument document = RedisUtils.get(key, KnowledgeDocument.class);
    
    if (document == null) {
        document = getDocumentFromDB(documentId);
        RedisUtils.set(key, document, 3600, TimeUnit.SECONDS);
    }
    
    return document;
}

// L3: 数据库
public KnowledgeDocument getDocumentFromDB(Long documentId) {
    return knowledgeDocumentDao.getById(documentId);
}
```

**缓存失效**：
```java
@CacheEvict(value = {"local:document", "redis:document"}, key = "#documentId")
public void invalidateDocumentCache(Long documentId) {
    // 同时失效本地缓存和 Redis 缓存
}
```

---

#### 4. 流式处理
**背压控制**：

```java
public Flux<String> streamChat(String prompt) {
    return Flux.create(sink -> {
        streamingChatLanguageModel.generate(prompt, new StreamingResponseHandler<AiMessage>() {
            @Override
            public void onNext(String token) {
                // 检查背压
                if (sink.requestedFromDownstream() > 0) {
                    sink.next(token);
                } else {
                    log.warn("Downstream is slow, buffering token");
                }
            }
        });
    }, FluxSink.OverflowStrategy.BUFFER);  // 缓冲策略
}
```

---

#### 5. 错误处理
**分层错误处理**：

```java
// 1. 业务异常
public class LLMApiException extends RuntimeException {
    private final String errorCode;
    private final String errorMessage;
}

// 2. 全局异常处理
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(LLMApiException.class)
    public ResponseEntity<ErrorResponse> handleLLMApiException(LLMApiException e) {
        log.error("LLM API 异常", e);
        
        ErrorResponse response = ErrorResponse.builder()
            .code(e.getErrorCode())
            .message("AI 服务暂时不可用，请稍后再试")
            .build();
        
        return ResponseEntity.status(503).body(response);
    }
}

// 3. 降级处理
@CircuitBreaker(name = "llmService", fallbackMethod = "chatFallback")
public String chat(String prompt) {
    return chatLanguageModel.generate(prompt);
}

private String chatFallback(String prompt, Throwable throwable) {
    log.warn("LLM service degraded", throwable);
    return "抱歉，AI 服务暂时不可用，请稍后再试。";
}
```


---

### 5.3 性能优化技巧

#### 1. 批量操作
**向量批量插入**：
```java
// 不推荐：逐个插入
for (DocumentChunk chunk : chunks) {
    vectorService.storeVector(documentId, chunk);  // N 次网络请求
}

// 推荐：批量插入
vectorService.storeVectors(documentId, chunks);  // 1 次网络请求
```

**性能提升**：
- 减少网络开销：N 次 → 1 次
- 减少事务开销
- 提高吞吐量

---

#### 2. 连接池管理
**Milvus 连接池**：
```java
@Configuration
public class MilvusConfig {
    
    @Bean
    public MilvusServiceClient milvusClient() {
        ConnectParam connectParam = ConnectParam.newBuilder()
            .withHost(host)
            .withPort(port)
            .withConnectTimeout(10, TimeUnit.SECONDS)
            .withKeepAliveTime(55, TimeUnit.SECONDS)      // 保持连接
            .withKeepAliveTimeout(20, TimeUnit.SECONDS)
            .build();
        
        return new MilvusServiceClient(connectParam);
    }
}
```

**HTTP 连接池**：
```java
@Bean
public OkHttpClient okHttpClient() {
    return new OkHttpClient.Builder()
        .connectionPool(new ConnectionPool(10, 5, TimeUnit.MINUTES))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build();
}
```

---

#### 3. 异步并发
**并行处理文档分块**：
```java
public List<DocumentChunk> processChunksInParallel(List<DocumentChunk> chunks) {
    return chunks.parallelStream()
        .map(chunk -> {
            // 生成向量
            float[] vector = embeddingService.generateEmbedding(chunk.getContent());
            chunk.setVector(vector);
            return chunk;
        })
        .collect(Collectors.toList());
}
```

**CompletableFuture 异步编排**：
```java
public CompletableFuture<RAGResponse> ragQueryAsync(RAGQueryRequest request) {
    // 1. 异步生成向量
    CompletableFuture<float[]> vectorFuture = CompletableFuture.supplyAsync(
        () -> embeddingService.generateEmbedding(request.getQuestion())
    );
    
    // 2. 异步检索
    CompletableFuture<List<SearchResult>> searchFuture = vectorFuture.thenApplyAsync(
        vector -> vectorService.search(vector, request.getTopK(), request.getDocumentId())
    );
    
    // 3. 异步生成回答
    return searchFuture.thenApplyAsync(results -> {
        String prompt = buildRAGPrompt(request.getQuestion(), results);
        String answer = llmService.chat(prompt, options);
        return new RAGResponse(answer, results);
    });
}
```

---

#### 4. 懒加载
**延迟初始化**：
```java
@Component
public class EmbeddingModelHolder {
    
    private volatile EmbeddingModel embeddingModel;
    
    public EmbeddingModel getEmbeddingModel() {
        if (embeddingModel == null) {
            synchronized (this) {
                if (embeddingModel == null) {
                    // 延迟加载，避免启动时加载大模型
                    embeddingModel = loadEmbeddingModel();
                }
            }
        }
        return embeddingModel;
    }
}
```

---

#### 5. 内存优化
**流式处理大文件**：
```java
public void processLargeDocument(String filePath) {
    try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
        String line;
        StringBuilder buffer = new StringBuilder();
        
        while ((line = reader.readLine()) != null) {
            buffer.append(line).append("\n");
            
            // 每 1000 行处理一次
            if (buffer.length() > 100000) {
                processChunk(buffer.toString());
                buffer.setLength(0);  // 清空缓冲区
            }
        }
        
        // 处理剩余内容
        if (buffer.length() > 0) {
            processChunk(buffer.toString());
        }
    }
}
```

---

### 5.4 监控与可观测性

#### 1. 日志设计
**结构化日志**：
```java
@Slf4j
public class RAGServiceImpl {
    
    public Flux<String> ragQuery(RAGQueryRequest request) {
        long startTime = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString();
        
        log.info("RAG查询开始 - traceId: {}, question: {}, documentId: {}, userId: {}", 
                 traceId, request.getQuestion(), request.getDocumentId(), request.getUserId());
        
        return ragQueryInternal(request)
            .doOnComplete(() -> {
                long duration = System.currentTimeMillis() - startTime;
                log.info("RAG查询完成 - traceId: {}, duration: {}ms", traceId, duration);
            })
            .doOnError(error -> {
                long duration = System.currentTimeMillis() - startTime;
                log.error("RAG查询失败 - traceId: {}, duration: {}ms, error: {}", 
                         traceId, duration, error.getMessage(), error);
            });
    }
}
```

---

#### 2. 指标收集
**Prometheus 指标**：
```java
@Component
public class AIMetrics {
    
    private final Counter ragQueryCounter;
    private final Timer ragQueryTimer;
    private final Gauge vectorStoreSize;
    
    public AIMetrics(MeterRegistry registry) {
        this.ragQueryCounter = Counter.builder("ai.rag.query.total")
            .description("Total RAG queries")
            .tag("status", "success")
            .register(registry);
        
        this.ragQueryTimer = Timer.builder("ai.rag.query.duration")
            .description("RAG query duration")
            .register(registry);
        
        this.vectorStoreSize = Gauge.builder("ai.vector.store.size", this::getVectorStoreSize)
            .description("Vector store size")
            .register(registry);
    }
    
    public void recordRAGQuery(Runnable query) {
        ragQueryTimer.record(() -> {
            query.run();
            ragQueryCounter.increment();
        });
    }
}
```

---

#### 3. 链路追踪
**Sleuth 集成**：
```java
@Component
public class RAGServiceImpl {
    
    @Autowired
    private Tracer tracer;
    
    public Flux<String> ragQuery(RAGQueryRequest request) {
        Span span = tracer.nextSpan().name("rag-query").start();
        
        try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
            span.tag("question", request.getQuestion());
            span.tag("documentId", String.valueOf(request.getDocumentId()));
            
            return ragQueryInternal(request)
                .doOnComplete(() -> span.finish())
                .doOnError(error -> {
                    span.error(error);
                    span.finish();
                });
        }
    }
}
```

---

## 六、迭代演进路线

### 6.1 第一阶段：简单聊天机器人（已完成）
**时间**：2024 年初
**功能**：
- ✅ 基础 GPT 对话
- ✅ 上下文管理
- ✅ 频率限制
- ✅ @ 触发机制

**技术栈**：
- OkHttp + OpenAI API
- Redis 存储上下文
- 自定义频率控制器

---

### 6.2 第二阶段：企业级 AI 模块（已完成）
**时间**：2025 年 1 月
**功能**：
- ✅ LangChain4j 集成
- ✅ 流式输出
- ✅ 向量存储（Milvus）
- ✅ RAG 知识问答
- ✅ 文档上传与索引
- ✅ 异步处理（RocketMQ）
- ✅ 缓存优化
- ✅ 熔断降级

**技术栈**：
- LangChain4j 0.27.1
- Milvus 2.3.4
- Apache Tika 2.9.1
- RocketMQ
- Redis 缓存

---

### 6.3 第三阶段：功能增强（规划中）
**预计时间**：2025 年 Q2

**计划功能**：
- [ ] 多模态支持（图片、语音）
- [ ] 对话历史管理
- [ ] 智能推荐
- [ ] 知识图谱集成
- [ ] 多租户支持
- [ ] 成本控制与计费

**技术选型**：
- 图片识别：GPT-4 Vision
- 语音识别：Whisper API
- 知识图谱：Neo4j

---

## 七、总结

### 7.1 核心亮点

1. **模块化设计**：清晰的模块划分，职责明确
2. **技术选型合理**：LangChain4j 适配现有环境
3. **流式输出**：提升用户体验
4. **RAG 架构**：检索增强生成，提高回答准确性
5. **异步处理**：不阻塞用户操作
6. **容错降级**：保证系统可用性
7. **性能优化**：缓存、批量操作、连接池
8. **可观测性**：日志、指标、链路追踪

---

### 7.2 学习要点

1. **设计模式**：策略、工厂、模板方法、责任链
2. **异步编程**：Reactor Flux、CompletableFuture
3. **向量检索**：Milvus、Embedding、相似度计算
4. **RAG 架构**：文档分块、向量检索、Prompt 工程
5. **容错机制**：熔断、降级、重试
6. **性能优化**：缓存、批量、并发
7. **可观测性**：日志、指标、追踪

---

### 7.3 面试要点

**问题 1**：如何实现流式输出？
- 使用 Reactor Flux + StreamingResponseHandler
- SSE（Server-Sent Events）协议
- 背压控制

**问题 2**：RAG 的核心流程是什么？
- 文档上传 → 分块 → 生成向量 → 存储
- 用户提问 → 生成向量 → 检索 Top-K → 构造 Prompt → LLM 生成

**问题 3**：如何保证系统可用性？
- 熔断降级：Resilience4j
- 重试机制：Spring Retry
- 异步处理：RocketMQ
- 缓存优化：Redis

**问题 4**：如何优化性能？
- 批量操作：减少网络开销
- 连接池：复用连接
- 缓存：多级缓存
- 并发：并行处理

**问题 5**：如何保证幂等性？
- 操作前检查状态
- 多次调用结果一致
- 不抛出异常

---

## 八、参考资料

- [LangChain4j 官方文档](https://docs.langchain4j.dev/)
- [Milvus 官方文档](https://milvus.io/docs)
- [OpenAI API 文档](https://platform.openai.com/docs/api-reference)
- [Reactor 官方文档](https://projectreactor.io/docs)
- [Resilience4j 官方文档](https://resilience4j.readme.io/)

