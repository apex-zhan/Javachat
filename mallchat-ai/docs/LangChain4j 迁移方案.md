# LangChain4j 迁移方案

## 📋 迁移概览

### 迁移目标
将 AI 模块从 Spring AI 迁移到 LangChain4j，保持功能不变，无需升级 Spring Boot 和 Java 版本。

### 迁移范围
- ✅ LLM 集成模块（mallchat-ai-llm）
- ✅ 向量存储模块（mallchat-ai-vector）
- ✅ RAG 引擎模块（mallchat-ai-rag）
- ✅ 智能助手模块（mallchat-ai-assistant）
- ✅ 配置文件（application-ai.yml）

### 迁移阶段

本次迁移分为两个阶段：

1. **第一阶段（已完成）**: 从 Spring AI 迁移到 LangChain4j 0.27.1
2. **第二阶段（已完成）**: 从 LangChain4j 0.27.1 升级到 0.36.0，并接入 Ollama + Qdrant 本地开源方案

### 迁移时间估算

- **第一阶段**: 3-4 小时
- **第二阶段**: 2-3 天（涉及 Qdrant、Ollama、多模型适配）

---

## 第一阶段：从 Spring AI 迁移到 LangChain4j

### 阶段 1：依赖调整（30 分钟）

#### 1.1 更新父 POM

```xml
<!-- mallchat-ai/pom.xml -->
<properties>
    <!-- 移除 Spring AI -->
    <!-- <spring-ai.version>1.1.3</spring-ai.version> -->
    
    <!-- 添加 LangChain4j -->
    <langchain4j.version>0.36.0</langchain4j.version>
    <langchain4j-spring-boot.version>0.36.0</langchain4j-spring-boot.version>
    
    <!-- 保持不变 -->
    <milvus-sdk.version>2.3.4</milvus-sdk.version>
    <qdrant-java-client.version>1.14.0</qdrant-java-client.version>
    <tika.version>2.9.1</tika.version>
    <jqwik.version>1.7.4</jqwik.version>
</properties>

<dependencyManagement>
    <dependencies>
        <!-- 移除 Spring AI OpenAI -->
        <!--
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
            <version>${spring-ai.version}</version>
        </dependency>
        -->
        
        <!-- LangChain4j Core -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>
        
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-open-ai</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>
        
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

#### 1.2 更新 LLM 模块 POM

```xml
<!-- mallchat-ai/mallchat-ai-llm/pom.xml -->
<dependencies>
    <!-- LangChain4j -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j</artifactId>
    </dependency>
    
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-open-ai</artifactId>
    </dependency>
    
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-ollama</artifactId>
    </dependency>
    
    <!-- 保持其他依赖不变 -->
    <dependency>
        <groupId>com.abin.mallchat</groupId>
        <artifactId>mallchat-ai-common</artifactId>
    </dependency>
    
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>
    
    <!-- 测试依赖 -->
    <dependency>
        <groupId>net.jqwik</groupId>
        <artifactId>jqwik</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

#### 1.3 更新 Vector 模块 POM

```xml
<!-- mallchat-ai/mallchat-ai-vector/pom.xml -->
<dependencies>
    <!-- LangChain4j -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j</artifactId>
    </dependency>
    
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-ollama</artifactId>
    </dependency>
    
    <!-- 向量数据库 -->
    <dependency>
        <groupId>io.milvus</groupId>
        <artifactId>milvus-sdk-java</artifactId>
    </dependency>
    
    <dependency>
        <groupId>io.qdrant</groupId>
        <artifactId>qdrant-java-client</artifactId>
    </dependency>
    
    <dependency>
        <groupId>com.abin.mallchat</groupId>
        <artifactId>mallchat-ai-common</artifactId>
    </dependency>
</dependencies>
```

---

### 阶段 2：配置文件调整

#### 2.1 推荐配置（Ollama + Qdrant）

```yaml
# mallchat-chat-server/src/main/resources/application-local.yml

spring:
  profiles:
    active: local

embedding:
  provider: bge

ollama:
  base-url: http://localhost:11434
  embedding-model: bge-large-zh-v1.5
  timeout: 60s
  max-retries: 3

vector:
  store:
    provider: qdrant

qdrant:
  host: localhost
  port: 6334
  collection-name: mallchat_knowledge
  grpc-timeout: 30
  use-tls: false

langchain4j:
  llm:
    provider: qwen
    fallback-provider: llama

ollama:
  base-url: http://localhost:11434
  model-name: qwen2.5:14b
  temperature: 0.7
  timeout: 120s
```

#### 2.2 兼容 OpenAI 配置

```yaml
# 兼容旧方案
langchain4j:
  llm:
    provider: openai
  openai:
    api-key: sk-xxx
    base-url: https://api.openai.com/v1
    chat-model:
      model-name: gpt-3.5-turbo
      temperature: 0.7
      max-tokens: 2000
    embedding-model:
      model-name: text-embedding-3-large
```

#### 2.3 Mock 模式配置

```yaml
spring:
  profiles:
    active: mock
  main:
    allow-bean-definition-overriding: true

langchain4j:
  llm:
    provider: mock

embedding:
  provider: mock

vector:
  store:
    provider: mock
```

---

### 阶段 3：代码重构

#### 3.1 LLM Service 接口

```java
public interface LLMService {
    Flux<String> streamChat(String prompt, LLMOptions options);
    String chat(String prompt, LLMOptions options);
    Flux<String> streamChat(List<ChatMessage> messages, LLMOptions options);
    String chat(List<ChatMessage> messages, LLMOptions options);
    int countTokens(String text);
}
```

#### 3.2 QwenLLMService 实现（Ollama）

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

#### 3.3 OllamaBgeEmbeddingService 实现

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
        return response.content().vector();
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

#### 3.4 QdrantVectorService 实现

```java
@Slf4j
@Service
@Profile("!mock")
@ConditionalOnProperty(name = "vector.store.provider", havingValue = "qdrant", matchIfMissing = true)
public class QdrantVectorService implements VectorService {

    @Value("${qdrant.host:localhost}")
    private String host;

    @Value("${qdrant.port:6334}")
    private Integer port;

    @Value("${qdrant.collection-name:mallchat_knowledge}")
    private String collectionName;

    private QdrantClient qdrantClient;

    @PostConstruct
    public void init() {
        QdrantGrpcClient.Builder grpcBuilder = QdrantGrpcClient.newBuilder(host, port, false);
        qdrantClient = new QdrantClient(grpcBuilder.build());
        createCollectionIfNotExists();
    }

    private void createCollectionIfNotExists() {
        // 使用动态向量，支持 1024/768 维
        Collections.CollectionOperationResponse response = qdrantClient.createCollectionAsync(
                collectionName,
                Collections.VectorParams.newBuilder()
                        .setDistance(Collections.Distance.Cosine)
                        .setOnDisk(true)
                        .setDynamic(true)
                        .build()
        ).get();
    }
}
```

---

### 阶段 4：测试验证

#### 4.1 编译验证

```bash
cd mallchat-ai
mvn clean compile
```

#### 4.2 单元测试

```bash
mvn test
```

#### 4.3 集成测试

```bash
# Mock 模式启动
cd ../mallchat-chat-server
mvn spring-boot:run -Dspring-boot.run.profiles=mock

# 测试 AI 助手接口
curl -X POST http://localhost:8080/api/ai/assistant/question \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{"userId": 10001, "question": "你好"}'

# 测试 RAG 接口
curl -X POST http://localhost:8080/api/stream/rag/query \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{"userId": 10001, "question": "MallChat 是什么？"}'
```

---

## 第二阶段：升级到 LangChain4j 0.36.0 + Ollama + Qdrant

### 升级原因

1. **Ollama 支持**: LangChain4j 0.36.0 原生支持 OllamaChatModel 和 OllamaEmbeddingModel
2. **本地部署**: 业务要求数据不出域，需要本地 LLM 和 Embedding
3. **动态向量**: Qdrant 支持动态向量，兼容多种 Embedding 维度
4. **Mock 模式**: 便于本地开发和测试

### 升级内容

| 组件 | 升级前 | 升级后 |
|------|--------|--------|
| LangChain4j | 0.27.1 | 0.36.0 |
| LLM | OpenAI API | Ollama + Qwen2.5-14B |
| Embedding | OpenAI API | Ollama + bge-large-zh-v1.5 |
| 向量库 | Milvus | Qdrant（动态向量） |
| 新增 | - | Mock 模式、LLMServiceFactory、多模型支持 |

### 关键变化

1. **新增依赖**: `langchain4j-ollama`、`qdrant-java-client`
2. **新增服务**: `QwenLLMService`、`LlamaLLMService`、`OllamaBgeEmbeddingService`、`M3eEmbeddingService`、`QdrantVectorService`
3. **新增工厂**: `LLMServiceFactory` 管理多 LLM 提供商
4. **新增配置**: `application-mock.yml` 支持 Mock 模式
5. **接口不变**: `LLMService` 和 `EmbeddingService` 接口保持稳定

---

## 📊 迁移对比

### 代码变化统计

| 文件类型 | 修改文件数 | 新增文件数 | 删除文件数 |
|---------|-----------|-----------|-----------|
| POM 文件 | 4 | 0 | 0 |
| 配置文件 | 2 | 1 | 0 |
| Java 类 | 3 | 8 | 0 |
| 测试类 | 1 | 0 | 0 |
| **总计** | **10** | **9** | **0** |

### API 对比

| 功能 | Spring AI | LangChain4j | 变化 |
|------|-----------|-------------|------|
| 流式聊天 | `ChatClient.stream()` | `StreamingChatLanguageModel.generate()` | ⚠️ API 变化 |
| 同步聊天 | `ChatClient.call()` | `ChatLanguageModel.generate()` | ⚠️ API 变化 |
| 生成向量 | `EmbeddingClient.embed()` | `EmbeddingModel.embed()` | ⚠️ API 变化 |
| Token 计数 | 需自己实现 | `OpenAiTokenizer.estimateTokenCountInText()` | ✅ 更方便 |
| Ollama 支持 | 不支持 | `OllamaChatModel`、`OllamaEmbeddingModel` | ✅ 新增 |

---

## ⚠️ 注意事项

### 1. 环境变量

生产环境建议通过环境变量注入：
```bash
export OLLAMA_BASE_URL=http://localhost:11434
export QDRANT_HOST=localhost
export QDRANT_PORT=6334
export OPENAI_API_KEY=sk-your-api-key
```

### 2. 依赖冲突

如果遇到依赖冲突，检查：
```bash
mvn dependency:tree | grep langchain4j
mvn dependency:tree | grep qdrant
```

### 3. 日志配置

```yaml
logging:
  level:
    dev.langchain4j: DEBUG
    com.abin.mallchat.ai: DEBUG
```

### 4. 超时配置

```yaml
ollama:
  timeout: 120s  # LLM 推理较慢时增加

qdrant:
  grpc-timeout: 30
```

### 5. Mock 模式

Mock 模式下必须设置：
```yaml
spring:
  main:
    allow-bean-definition-overriding: true
```

---

## 🎯 迁移检查清单

- [x] 更新所有 POM 文件
- [x] 更新配置文件 application-ai.yml / application-local.yml / application-mock.yml
- [x] 创建 LangChain4jConfig 配置类
- [x] 创建 QwenLLMService / LlamaLLMService
- [x] 创建 OllamaBgeEmbeddingService / M3eEmbeddingService
- [x] 创建 QdrantVectorService
- [x] 创建 LLMServiceFactory
- [x] 创建 Mock 系列实现
- [x] 更新单元测试
- [x] 运行所有测试确保通过
- [x] 启动应用验证功能
- [x] 测试流式输出
- [x] 测试向量生成
- [x] 更新文档

---

## 📚 相关文档

- [LangChain4j 官方文档](https://docs.langchain4j.dev/)
- [LangChain4j GitHub](https://github.com/langchain4j/langchain4j)
- [Ollama 官方文档](https://ollama.com/)
- [Qdrant 官方文档](https://qdrant.tech/documentation/)
- [AI技术方案](../AI技术方案.md)
- [部署运维指南](../../mallchat-ai-rag/docs/部署运维指南.md)

---

## 🆘 常见问题

### Q1: 依赖下载失败怎么办？
A: 检查 Maven 仓库配置，确保可以访问 Maven Central。

### Q2: Ollama 无法连接怎么办？
A: 检查 Ollama 是否已启动：`curl http://localhost:11434/api/tags`；检查 `ollama.base-url` 配置。

### Q3: Qdrant gRPC 端口是多少？
A: 默认 REST 端口是 6333，Java SDK 使用 gRPC 端口 6334，配置时注意区分。

### Q4: 流式输出不工作怎么办？
A: 检查是否正确使用了 `StreamingChatLanguageModel` 和 `StreamingResponseHandler`。

### Q5: Mock 模式下 Bean 冲突怎么办？
A: 确保设置 `spring.main.allow-bean-definition-overriding: true`。

---

## ✅ 迁移完成标志

1. ✅ 所有测试通过
2. ✅ 应用可以正常启动
3. ✅ LLM 调用正常工作
4. ✅ 流式输出正常工作
5. ✅ Embedding 生成正常工作
6. ✅ 向量检索正常工作
7. ✅ Mock 模式正常工作
8. ✅ 无编译错误和警告
9. ✅ 日志输出正常
10. ✅ 性能符合预期

---

## 📝 更新日志

| 日期 | 版本 | 更新内容 |
|------|------|---------|
| 2026-06-13 | v2.0 | 补充第二阶段升级：LangChain4j 0.36.0 + Ollama + Qdrant + Mock 模式 |
| 2025-01-05 | v1.0 | 完成从 Spring AI 到 LangChain4j 的迁移方案 |

---

*本文档由 AI Assistant 维护，如有问题请及时反馈。*
