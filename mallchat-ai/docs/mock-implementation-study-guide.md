# MallChat AI 模块 Mock 实现学习指南

> **文档定位**: 本文档面向希望深入理解 MallChat AI 模块 Mock 实现原理的开发者和面试者。
> 
> **适用场景**: 本地开发、单元测试、集成测试、CI/CD 流水线、面试复习。
> 
> **前置知识**: Spring Boot、Spring Profile、响应式编程（Reactor）、基本向量运算。

---

## 📑 目录

- [1. 概述](#1-概述)
  - [1.1 什么是 Mock 实现](#11-什么是-mock-实现)
  - [1.2 为什么需要 Mock](#12-为什么需要-mock)
  - [1.3 Mock vs Stub vs Fake](#13-mock-vs-stub-vs-fake)
- [2. 架构设计](#2-架构设计)
  - [2.1 整体架构图](#21-整体架构图)
  - [2.2 Spring Profile 机制](#22-spring-profile-机制)
  - [2.3 服务工厂模式](#23-服务工厂模式)
  - [2.4 Bean 覆盖机制](#24-bean-覆盖机制)
- [3. Mock 实现详解](#3-mock-实现详解)
  - [3.1 MockLLMService - 大语言模型 Mock](#31-mockllmservice---大语言模型-mock)
  - [3.2 MockEmbeddingService - 向量生成 Mock](#32-mockembeddingservice---向量生成-mock)
  - [3.3 MockVectorService - 向量数据库 Mock](#33-mockvectorservice---向量数据库-mock)
  - [3.4 MockFineTuneClient - 微调服务 Mock](#34-mockfinetuneclient---微调服务-mock)
- [4. 配置与启用](#4-配置与启用)
  - [4.1 application-mock.yml 详解](#41-application-mockyml-详解)
  - [4.2 启动命令](#42-启动命令)
  - [4.3 切换真实服务](#43-切换真实服务)
- [5. 测试场景](#5-测试场景)
  - [5.1 单元测试（@Mock）](#51-单元测试mock)
  - [5.2 集成测试（@SpringBootTest + @ActiveProfiles）](#52-集成测试springboottest--activeprofiles)
  - [5.3 属性测试（Property-Based Testing）](#53-属性测试property-based-testing)
  - [5.4 Mock 在降级测试中的应用](#54-mock-在降级测试中的应用)
- [6. 最佳实践](#6-最佳实践)
  - [6.1 Mock 设计原则](#61-mock-设计原则)
  - [6.2 常见问题与解决方案](#62-常见问题与解决方案)
  - [6.3 性能考虑](#63-性能考虑)
- [7. 面试高频问题](#7-面试高频问题)
- [8. 源码速查表](#8-源码速查表)
- [9. 扩展阅读](#9-扩展阅读)

---

## 1. 概述

### 1.1 什么是 Mock 实现

Mock 实现是一种**替代真实服务**的轻量级实现，它：
- ✅ 实现与真实服务**相同的接口**
- ✅ 返回**预设的、确定性的**响应
- ✅ **不依赖**外部基础设施（如 Ollama、Qdrant、OpenAI API）
- ✅ 使用**内存数据**，重启后丢失

在 MallChat AI 模块中，我们为以下 4 个核心组件编写了 Mock 实现：

| 组件 | 真实实现 | Mock 实现 | 文件路径 |
|------|---------|----------|---------|
| LLM 服务 | OpenAILLMService, QwenLLMService | **MockLLMService** | `mallchat-ai-llm/.../impl/MockLLMService.java` |
| Embedding 服务 | OllamaBgeEmbeddingService | **MockEmbeddingService** | `mallchat-ai-vector/.../impl/MockEmbeddingService.java` |
| 向量存储 | QdrantVectorService, MilvusVectorService | **MockVectorService** | `mallchat-ai-vector/.../impl/MockVectorService.java` |
| 微调服务 | FineTuneClient (HTTP) | **MockFineTuneClient** | `mallchat-ai-finetune/.../MockFineTuneClient.java` |

### 1.2 为什么需要 Mock

#### 🎯 场景一：新成员 onboarding
> 小王刚入职，想跑通项目。如果没有 Mock，他需要：
> 1. 安装 Ollama（~2GB）
> 2. 下载 qwen2.5:14b 模型（~9GB）
> 3. 启动 Qdrant Docker 容器
> 4. 配置各种 API Key
> 
> **有了 Mock**：`mvn spring-boot:run -Dspring.profiles.active=mock`，5 分钟跑通。

#### 🎯 场景二：CI/CD 流水线
> 每次提交代码都调用真实 LLM？
> - ❌ 成本爆炸（OpenAI API 按 token 计费）
> - ❌ 网络不稳定导致测试失败
> - ❌ 测试执行时间不可控
> 
> **有了 Mock**：测试 100% 确定性，零成本，秒级执行。

#### 🎯 场景三：边界条件测试
> 如何测试"LLM 返回 10 万字超长文本"？
> - ❌ 真实 API 难以构造
> - ✅ Mock 直接返回超大字符串

### 1.3 Mock vs Stub vs Fake

| 类型 | 定义 | 本项目的例子 |
|------|------|------------|
| **Mock** | 验证行为（是否被调用、调用次数） | Mockito 的 `@Mock` 注解 |
| **Stub** | 预设返回值 | `when(mock.method()).thenReturn(x)` |
| **Fake** | 轻量级工作实现 | **MockLLMService、MockVectorService**（本项目的"Mock"实际是 Fake）|

> 💡 **术语辨析**：本项目文件名为 `MockXxxService`，但按照经典测试术语，它们实际上是 **Fake**——有真实的工作实现，不是简单的 Stub。

---

## 2. 架构设计

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────┐
│                    Spring Boot Application                   │
│                                                              │
│   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│   │  LLMService  │  │EmbeddingService│ │ VectorService│      │
│   │   (interface)│  │   (interface)  │ │  (interface) │      │
│   └──────┬───────┘  └──────┬───────┘  └──────┬───────┘      │
│          │                 │                 │              │
│   ┌──────┴───────┐  ┌──────┴───────┐  ┌──────┴───────┐      │
│   │ @Profile("!") │  │ @Profile("!") │  │ @Profile("!") │      │
│   │ OpenAILLM    │  │ OllamaBge    │  │ QdrantVector │      │
│   │   Service    │  │  Embedding   │  │   Service    │      │
│   └──────────────┘  └──────────────┘  └──────────────┘      │
│                                                              │
│   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│   │ @Profile("mock")│ │ @Profile("mock")│ │ @Profile("mock")│      │
│   │ MockLLM      │  │ MockEmbedding│  │ MockVector   │      │
│   │   Service    │  │   Service    │  │   Service    │      │
│   └──────────────┘  └──────────────┘  └──────────────┘      │
│                                                              │
│   激活方式: spring.profiles.active=mock                      │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 Spring Profile 机制

Spring Profile 是 Spring Boot 提供的**环境隔离**机制，核心原理：

```java
@Service
@Profile("mock")  // 只有当 spring.profiles.active 包含 "mock" 时才实例化
public class MockLLMService implements LLMService {
    // ...
}
```

```java
@Service
@Profile("!mock") // 当 spring.profiles.active 不包含 "mock" 时才实例化
public class OpenAILLMService implements LLMService {
    // ...
}
```

**关键配置**（`application-mock.yml`）：

```yaml
spring:
  profiles:
    active: mock
    group:
      mock: ai,mock  # mock profile 自动激活 ai 和 mock 两个 profile
```

### 2.3 服务工厂模式

`LLMServiceFactory` 实现了**运行时动态选择** LLM 提供商的能力：

```java
@Component
public class LLMServiceFactory {
    
    @Value("${langchain4j.llm.provider:openai}")
    private String defaultProvider;
    
    private Map<LLMProvider, LLMService> serviceMap = new HashMap<>();
    
    @PostConstruct
    public void init() {
        // 从 Spring 容器获取所有 LLMService 实现
        Map<String, LLMService> beans = applicationContext.getBeansOfType(LLMService.class);
        
        for (Map.Entry<String, LLMService> entry : beans.entrySet()) {
            String beanName = entry.getKey();
            
            // 根据 bean 名称映射到提供商
            if (beanName.toLowerCase().contains("mock")) {
                serviceMap.put(LLMProvider.MOCK, service);
            } else if (beanName.toLowerCase().contains("openai")) {
                serviceMap.put(LLMProvider.OPENAI, service);
            }
            // ... 其他提供商
        }
    }
    
    public LLMService getDefaultService() {
        LLMProvider provider = LLMProvider.fromCode(defaultProvider);
        return serviceMap.get(provider);
    }
}
```

**设计亮点**：
- ✅ 通过 `applicationContext.getBeansOfType()` 自动发现所有实现
- ✅ 运行时切换提供商（`switchDefaultProvider`）
- ✅ 支持降级服务（`fallbackProvider`）
- ✅ 新增提供商无需修改工厂代码（约定优于配置）

### 2.4 Bean 覆盖机制

Mock 模式需要**Bean 定义覆盖**支持：

```yaml
# application-mock.yml
spring:
  main:
    allow-bean-definition-overriding: true  # 允许 Mock Bean 覆盖真实 Bean
```

**工作原理**：
1. Spring 启动时扫描所有 `@Component`
2. `MockLLMService` 和 `OpenAILLMService` 都实现了 `LLMService`
3. 在 `mock` profile 下，两个 Bean 都会被扫描到
4. `allow-bean-definition-overriding: true` 允许后注册的 Bean 覆盖前者
5. `@Profile("mock")` 确保只有 Mock 实现被激活

> ⚠️ **注意**：Spring Boot 2.1+ 默认禁止 Bean 覆盖（防止意外冲突），Mock 模式必须显式开启。

---

## 3. Mock 实现详解

### 3.1 MockLLMService - 大语言模型 Mock

#### 文件位置
`mallchat-ai/mallchat-ai-llm/src/main/java/com/abin/mallchat/ai/llm/service/impl/MockLLMService.java`

#### 接口定义

```java
public interface LLMService {
    Flux<String> streamChat(String prompt, LLMOptions options);           // 流式调用
    String chat(String prompt, LLMOptions options);                       // 非流式调用
    Flux<String> streamChat(List<ChatMessage> messages, LLMOptions options); // 多轮对话流式
    String chat(List<ChatMessage> messages, LLMOptions options);          // 多轮对话非流式
    int countTokens(String text);                                         // Token 计数
}
```

#### 源码逐行解析

```java
@Slf4j
@Service
@Profile("mock")
public class MockLLMService implements LLMService {

    // 预设回复模板，%s 会被替换为用户提问
    private static final String MOCK_REPLY = 
        "【Mock模式】这是一个模拟回复。当前没有部署真实的LLM服务" +
        "（如Qwen2.5-14B或Llama3-70B），请通过Ollama部署后切换配置。\n\n" +
        "您的提问是：%s";

    @Override
    public Flux<String> streamChat(String prompt, LLMOptions options) {
        log.info("[Mock] streamChat called, prompt length: {}", prompt.length());
        String reply = String.format(MOCK_REPLY, prompt);
        
        // 🔑 核心技巧：将字符串拆分成字符数组，逐个发射，模拟真实 LLM 的流式输出
        return Flux.fromArray(reply.split(""))
                .delayElements(java.time.Duration.ofMillis(10)); // 模拟打字效果
    }

    @Override
    public String chat(String prompt, LLMOptions options) {
        log.info("[Mock] chat called, prompt length: {}", prompt.length());
        return String.format(MOCK_REPLY, prompt);
    }

    @Override
    public Flux<String> streamChat(List<ChatMessage> messages, LLMOptions options) {
        // 多轮对话：取最后一条消息作为用户当前提问
        String lastMessage = messages.isEmpty() ? "" : messages.get(messages.size() - 1).text();
        String reply = String.format(MOCK_REPLY, lastMessage);
        return Flux.fromArray(reply.split(""))
                .delayElements(java.time.Duration.ofMillis(10));
    }

    @Override
    public int countTokens(String text) {
        // 简单估算：中文字符约 1.5 字符/token，英文约 4 字符/token
        int chinese = 0, other = 0;
        for (char c : text.toCharArray()) {
            if (c >= 0x4E00 && c <= 0x9FA5) chinese++;  // Unicode 中文字符范围
            else other++;
        }
        return (int) (chinese / 1.5 + other / 4.0);
    }
}
```

#### 关键技术点

| 技术点 | 说明 |
|--------|------|
| `Flux.fromArray(reply.split(""))` | 将字符串拆分为字符流，模拟 LLM 的 token-by-token 输出 |
| `.delayElements(Duration.ofMillis(10))` | 每个字符延迟 10ms，产生"打字机效果" |
| `Unicode 范围 0x4E00-0x9FA5` | 判断中文字符的标准方法 |
| Token 估算公式 | 中文: `length/1.5`，英文: `length/4`（近似 BPE 分词效果） |

#### 面试问答

> **Q: 为什么要将字符串拆分成字符逐个返回，而不是一次性返回？**
>
> A: 真实 LLM API（如 OpenAI、Ollama）都是流式返回的，前端已经按照流式响应做了 UI 处理（如打字机效果、逐字显示）。如果 Mock 一次性返回，前端可能无法正常展示，或者用户体验不一致。`Flux.fromArray(reply.split("")).delayElements(...)` 完美模拟了真实行为。

---

### 3.2 MockEmbeddingService - 向量生成 Mock

#### 文件位置
`mallchat-ai/mallchat-ai-vector/src/main/java/com/abin/mallchat/ai/vector/service/impl/MockEmbeddingService.java`

#### 接口定义

```java
public interface EmbeddingService {
    float[] generateEmbedding(String text);           // 单个文本 → 向量
    List<float[]> generateEmbeddings(List<String> texts); // 批量 → 向量列表
}
```

#### 源码逐行解析

```java
@Slf4j
@Service
@Profile("mock")
public class MockEmbeddingService implements EmbeddingService {

    private static final int DEFAULT_DIMENSION = 1024;  // 兼容 bge-large-zh-v1.5
    private static final float NORMALIZATION_FACTOR = 1000.0f;

    @Override
    public float[] generateEmbedding(String text) {
        float[] vector = generateDeterministicVector(text, DEFAULT_DIMENSION);
        return vector;
    }

    @Override
    public List<float[]> generateEmbeddings(List<String> texts) {
        List<float[]> embeddings = new ArrayList<>();
        for (String text : texts) {
            embeddings.add(generateEmbedding(text));
        }
        return embeddings;
    }

    /**
     * 🔑 核心算法：基于 MD5 哈希生成确定性伪随机向量
     * 
     * 关键特性：相同文本 → 相同 MD5 → 相同向量
     * 这保证了 Mock 模式下语义检索的一致性
     */
    private float[] generateDeterministicVector(String text, int dimension) {
        try {
            // Step 1: MD5 哈希 → 128 位确定性摘要
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
            // hash 长度 = 16 字节（128 位）

            float[] vector = new float[dimension];
            double sumSquares = 0.0;

            // Step 2: 基于哈希值生成伪随机数
            for (int i = 0; i < dimension; i++) {
                // 从 16 字节哈希中提取 4 字节整数（int）
                // 使用多个字节组合，避免重复模式
                int seed = (hash[i % hash.length] & 0xFF) |           // 第 i 个字节
                        ((hash[(i + 7) % hash.length] & 0xFF) << 8) |  // 偏移 7
                        ((hash[(i + 13) % hash.length] & 0xFF) << 16) | // 偏移 13
                        ((hash[(i + 19) % hash.length] & 0xFF) << 24);  // 偏移 19

                // Step 3: 线性同余生成器（LCG）生成伪随机数
                // LCG 公式: X_{n+1} = (a * X_n + c) mod m
                // 这里 a = 1103515245, c = 12345 (glibc 的标准参数)
                long value = ((long) seed * 1103515245L + 12345L) & 0x7fffffffL;
                vector[i] = (float) (value / NORMALIZATION_FACTOR);  // 缩放到 [0, ~2000]
                sumSquares += vector[i] * vector[i];
            }

            // Step 4: L2 归一化（单位向量）
            // 归一化后向量长度 = 1，便于余弦相似度计算
            float norm = (float) Math.sqrt(sumSquares);
            if (norm > 0) {
                for (int i = 0; i < dimension; i++) {
                    vector[i] /= norm;
                }
            }

            return vector;

        } catch (NoSuchAlgorithmException e) {
            // 降级方案：MD5 不可用时使用简单哈希
            log.warn("[Mock] MD5 not available, using fallback hash");
            return generateFallbackVector(text, dimension);
        }
    }

    private float[] generateFallbackVector(String text, int dimension) {
        float[] vector = new float[dimension];
        double sumSquares = 0.0;

        for (int i = 0; i < dimension; i++) {
            // hashCode() + i * 31 是 Java 中经典的哈希组合方式
            int hash = text.hashCode() + i * 31;
            vector[i] = (float) ((hash % 1000) / 1000.0);
            sumSquares += vector[i] * vector[i];
        }

        // 同样进行 L2 归一化
        float norm = (float) Math.sqrt(sumSquares);
        if (norm > 0) {
            for (int i = 0; i < dimension; i++) {
                vector[i] /= norm;
            }
        }

        return vector;
    }
}
```

#### 算法流程图

```
输入文本: "Spring Boot"
    │
    ▼
MD5("Spring Boot") = [0x3A, 0x7F, 0x12, ...] (16 字节)
    │
    ▼
对于 i = 0 到 1023:
    ├─ 提取 4 字节种子: seed = hash[i%16] | hash[(i+7)%16]<<8 | ...
    ├─ LCG: value = (seed * 1103515245 + 12345) & 0x7fffffff
    └─ vector[i] = value / 1000.0
    │
    ▼
L2 归一化: vector[i] = vector[i] / sqrt(sum(vector²))
    │
    ▼
输出: 1024 维单位向量（相同文本始终产生相同向量）
```

#### 关键技术点

| 技术点 | 说明 |
|--------|------|
| **MD5 哈希** | 生成 128 位确定性摘要，相同输入始终相同输出 |
| **多字节组合** | `i, i+7, i+13, i+19` 偏移取字节，避免相邻维度相关性 |
| **LCG 算法** | `a=1103515245, c=12345` 是 glibc 的标准参数，分布均匀 |
| **L2 归一化** | `vector = vector / ||vector||`，使余弦相似度简化为点积 |
| **降级方案** | MD5 不可用时使用 `hashCode() + 31*i`，保证鲁棒性 |

#### 面试问答

> **Q: 为什么要生成"确定性"向量，而不是完全随机？**
>
> A: 如果每次调用都生成随机向量，那么同一篇文档的向量会变化，导致向量检索结果不一致。确定性向量保证"相同文本 → 相同向量 → 相同检索结果"，这是 Mock 模式下测试稳定性的关键。

> **Q: L2 归一化有什么作用？**
>
> A: 归一化后向量长度为 1，余弦相似度公式 `cos(θ) = (A·B) / (||A|| * ||B||)` 简化为 `cos(θ) = A·B`。计算更快，且只关注方向不关注长度，符合语义相似度的直觉。

---

### 3.3 MockVectorService - 向量数据库 Mock

#### 文件位置
`mallchat-ai/mallchat-ai-vector/src/main/java/com/abin/mallchat/ai/vector/service/impl/MockVectorService.java`

#### 接口定义

```java
public interface VectorService {
    void storeVectors(Long documentId, List<DocumentChunk> chunks);           // 存储向量
    List<SearchResult> search(float[] queryVector, int topK, Long documentId); // 相似度检索
    void deleteVectors(Long documentId);                                       // 删除向量（幂等）
    boolean exists(Long documentId);                                           // 检查存在性
}
```

#### 源码逐行解析

```java
@Slf4j
@Service
@Profile("mock")
public class MockVectorService implements VectorService {

    /**
     * 内存存储结构：documentId → List<ChunkVector>
     * 
     * 使用 ConcurrentHashMap 保证线程安全
     * 支持多线程并发上传和查询
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

        // 将 DocumentChunk 转换为内部 ChunkVector 结构
        List<ChunkVector> vectors = chunks.stream()
                .map(chunk -> ChunkVector.builder()
                        .documentId(documentId)
                        .chunkId(chunk.getId())
                        .chunkIndex(chunk.getChunkIndex())
                        .content(chunk.getContent())
                        .vector(extractVector(chunk))  // 从 metadata 提取向量
                        .metadata(chunk.getMetadata())
                        .build())
                .collect(Collectors.toList());

        storage.put(documentId, vectors);  // ConcurrentHashMap 线程安全
        log.info("[Mock] Successfully stored {} vectors for document: {}", vectors.size(), documentId);
    }

    @Override
    public List<SearchResult> search(float[] queryVector, int topK, Long documentId) {
        List<ScoredResult> allResults = new ArrayList<>();

        // 🔑 全表扫描：遍历所有文档的所有 chunk
        for (Map.Entry<Long, List<ChunkVector>> entry : storage.entrySet()) {
            Long docId = entry.getKey();

            // 如果指定了 documentId，只搜索该文档（过滤）
            if (documentId != null && !documentId.equals(docId)) {
                continue;
            }

            // 计算每个 chunk 与查询向量的余弦相似度
            for (ChunkVector chunk : entry.getValue()) {
                double similarity = cosineSimilarity(queryVector, chunk.getVector());
                allResults.add(new ScoredResult(chunk, similarity));
            }
        }

        // 按相似度降序排序
        allResults.sort((a, b) -> Double.compare(b.similarity, a.similarity));

        // 取前 topK 个
        return allResults.stream()
                .limit(topK)
                .map(scored -> SearchResult.builder()
                        .documentId(scored.chunk.getDocumentId())
                        .chunkId(scored.chunk.getChunkId())
                        .content(scored.chunk.getContent())
                        .score((float) scored.similarity)
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 计算余弦相似度
     * 
     * 公式: cos(θ) = (A·B) / (||A|| * ||B||)
     * 如果 A 和 B 都是单位向量（L2 归一化后），简化为: cos(θ) = A·B
     */
    private double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];  // 点积
            normA += a[i] * a[i];        // ||A||²
            normB += b[i] * b[i];        // ||B||²
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;  // 避免除以零
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 从 DocumentChunk 的 metadata JSON 中提取 embedding 数组
     * 
     * 示例 metadata: {"embedding": [0.1, 0.2, 0.3, ...], "chunkIndex": 1}
     */
    private float[] extractVector(DocumentChunk chunk) {
        String metadata = chunk.getMetadata();
        if (metadata == null || metadata.isEmpty()) {
            return new float[0];
        }

        try {
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
            log.warn("[Mock] Failed to extract vector from metadata");
        }

        return new float[0];
    }
}
```

#### 数据结构图

```
┌──────────────────────────────────────────────────────┐
│  ConcurrentHashMap<Long, List<ChunkVector>> storage  │
├──────────────────────────────────────────────────────┤
│                                                      │
│  Key: 1L ───────→ [ChunkVector, ChunkVector, ...]    │
│  Key: 2L ───────→ [ChunkVector, ChunkVector]         │
│  Key: 3L ───────→ [ChunkVector, ...]                 │
│                                                      │
│  ChunkVector {                                       │
│      documentId: 1L                                  │
│      chunkId: 101L                                   │
│      chunkIndex: 0                                   │
│      content: "Spring Boot 是一个框架..."              │
│      vector: [0.1, -0.2, 0.3, ...] (1024 维)         │
│      metadata: "{\"chunkIndex\": 0, ...}"             │
│  }                                                   │
│                                                      │
└──────────────────────────────────────────────────────┘
```

#### 检索流程

```
查询向量 Q = [0.1, 0.2, 0.3, ...]
    │
    ▼
┌─────────────────────────────────────┐
│ 遍历 storage 中所有文档              │
│ 如果指定了 documentId，过滤其他文档   │
└─────────────────────────────────────┘
    │
    ▼
对每个 ChunkVector C:
    similarity = cosineSimilarity(Q, C.vector)
    存入 allResults
    │
    ▼
按 similarity 降序排序
    │
    ▼
取前 topK 个结果
    │
    ▼
返回 List<SearchResult>
```

#### 关键技术点

| 技术点 | 说明 |
|--------|------|
| `ConcurrentHashMap` | 线程安全，支持并发读写 |
| 全表扫描 | Mock 实现无需索引，直接遍历（数据量小） |
| 余弦相似度 | 标准向量相似度度量，范围 [-1, 1] |
| 空值/长度检查 | 防御性编程，避免 NPE 和越界 |
| JSON 解析 | 从 metadata 中提取 embedding 数组 |

#### 面试问答

> **Q: 为什么 MockVectorService 用全表扫描而不是构建索引？**
>
> A: Mock 模式面向开发和测试，数据量通常很小（<1000 条）。全表扫描简单、无依赖、足够快。真实场景使用 Qdrant 或 Milvus，它们使用 HNSW（Hierarchical Navigable Small World）等近似最近邻算法，支持百万级向量的毫秒级检索。

> **Q: ConcurrentHashMap 在这里的作用是什么？**
>
> A: Spring Boot 默认是单例模式，VectorService 会被多个请求共享。ConcurrentHashMap 保证并发上传和并发查询时的线程安全，避免 `ConcurrentModificationException`。

---

### 3.4 MockFineTuneClient - 微调服务 Mock

#### 文件位置
`mallchat-ai/mallchat-ai-finetune/src/main/java/com/abin/mallchat/ai/finetune/client/MockFineTuneClient.java`

#### 继承关系

```java
// 真实客户端
@Component
@Profile("!mock")
public class FineTuneClient {
    // WebClient HTTP 调用
}

// Mock 客户端
@Component
@Profile("mock")
public class MockFineTuneClient extends FineTuneClient {
    // 模拟实现
}
```

**关键设计**：MockFineTuneClient **继承** FineTuneClient，这样可以：
- 复用父类的接口定义
- 通过 `@Profile("mock")` 自动覆盖父类 Bean
- 保持代码结构一致

#### 源码逐行解析

```java
@Slf4j
@Component
@Profile("mock")
public class MockFineTuneClient extends FineTuneClient {

    // 内存存储：taskId → MockTask
    private final ConcurrentHashMap<String, MockTask> tasks = new ConcurrentHashMap<>();

    @PostConstruct
    @Override
    public void init() {
        log.info("[Mock] MockFineTuneClient initialized");
        log.warn("[Mock] 微调服务为模拟实现，所有任务返回模拟数据！");
    }

    @Override
    public Mono<FineTuneResponse> submitFineTune(FineTuneRequest request) {
        // 生成模拟任务 ID: MOCK-XXXX
        String taskId = "MOCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        
        MockTask task = new MockTask(taskId, request.getBaseModel(), request.getProvider());
        tasks.put(taskId, task);

        // 🔑 核心技巧：启动后台线程模拟异步训练过程
        new Thread(() -> simulateTraining(task)).start();

        // 立即返回，不等待训练完成（真实 API 也是异步的）
        return Mono.just(FineTuneResponse.builder()
                .taskId(taskId)
                .status("pending")
                .baseModel(request.getBaseModel())
                .outputPath("/mock/outputs/" + taskId)
                .createdAt(LocalDateTime.now())
                .build());
    }

    @Override
    public Mono<FineTuneStatusResponse> getTaskStatus(String taskId) {
        MockTask task = tasks.get(taskId);
        if (task == null) {
            return Mono.error(new RuntimeException("Task not found: " + taskId));
        }

        return Mono.just(FineTuneStatusResponse.builder()
                .taskId(taskId)
                .status(task.getStatus())       // pending → running → completed
                .progress(task.getProgress())   // 0 → 100
                .latestLog("[Mock] " + task.getLatestLog())
                .build());
    }

    @Override
    public Mono<Void> cancelTask(String taskId) {
        MockTask task = tasks.get(taskId);
        if (task != null) {
            task.setStatus("cancelled");
        }
        return Mono.empty();
    }

    /**
     * 🔑 模拟训练过程
     * 
     * 在后台线程中逐步更新任务状态，模拟真实的异步训练
     */
    private void simulateTraining(MockTask task) {
        try {
            task.setStatus("running");
            
            // 模拟训练日志序列
            String[] logs = {
                    "Loading model...",
                    "Preparing dataset...",
                    "Starting training epoch 1/3...",
                    "Epoch 1 completed, loss: 2.345",
                    "Starting training epoch 2/3...",
                    "Epoch 2 completed, loss: 1.876",
                    "Starting training epoch 3/3...",
                    "Epoch 3 completed, loss: 1.234",
                    "Saving adapter weights...",
                    "Training completed!"
            };

            for (int i = 0; i < logs.length; i++) {
                Thread.sleep(500);  // 每步 500ms，总共约 5 秒
                task.setLatestLog(logs[i]);
                task.setProgress((i + 1) * 100 / logs.length);  // 10% → 100%
            }

            task.setStatus("completed");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            task.setStatus("failed");
        }
    }

    // 内部类：模拟任务状态
    private static class MockTask {
        private final String taskId;
        private volatile String status;     // volatile 保证线程可见性
        private volatile int progress;       // 0-100
        private volatile String latestLog;   // 最新日志

        MockTask(String taskId, String baseModel, String provider) {
            this.taskId = taskId;
            this.status = "pending";
            this.progress = 0;
            this.latestLog = "Task created";
        }
        // ... getters/setters
    }
}
```

#### 任务状态流转

```
提交任务 (submitFineTune)
    │
    ▼
pending ───────────────────────┐
    │                          │
    │ (simulateTraining 线程)   │ (cancelTask)
    ▼                          ▼
running ───→ completed      cancelled
    │
    │ (InterruptedException)
    ▼
 failed
```

#### 关键技术点

| 技术点 | 说明 |
|--------|------|
| `extends FineTuneClient` | 继承而非实现接口，复用代码结构 |
| `new Thread(...).start()` | 模拟异步训练过程 |
| `volatile` | 保证多线程间状态可见性 |
| `Thread.sleep(500)` | 每步 500ms，控制模拟速度 |
| `ConcurrentHashMap` | 线程安全的任务存储 |
| `UUID.randomUUID()` | 生成唯一任务 ID |

#### 面试问答

> **Q: 为什么 MockFineTuneClient 选择继承 FineTuneClient 而不是实现接口？**
>
> A: 继承可以复用父类的字段和方法（如 `FineTuneConfig`、`WebClient` 配置），同时通过 `@Profile("mock")` 自动覆盖父类 Bean。如果以后 FineTuneClient 增加新方法，Mock 版本会自动获得默认实现（虽然需要手动覆盖）。

> **Q: `volatile` 关键字在这里的作用是什么？**
>
> A: 后台训练线程修改 `status`、`progress`，主线程通过 `getTaskStatus` 读取。没有 `volatile`，主线程可能读取到缓存中的旧值。`volatile` 保证变量的读写直接操作主内存，实现线程间可见性。

---

## 4. 配置与启用

### 4.1 application-mock.yml 详解

```yaml
# Mock 模式配置
# 用于本地开发/测试，无需部署 Ollama、Qdrant、LLM 等外部服务
# 启动方式：spring.profiles.active=mock

spring:
  main:
    # 允许 Bean 定义覆盖（Mock 实现会覆盖真实实现）
    # Spring Boot 2.1+ 默认关闭，必须显式开启
    allow-bean-definition-overriding: true

# ==================== AI 配置 ====================
langchain4j:
  llm:
    provider: mock  # 使用 MockLLMService

embedding:
  provider: mock  # 使用 MockEmbeddingService

vector:
  store:
    provider: mock  # 使用 MockVectorService

finetune:
  service-url: http://localhost:8000  # Mock 模式不调用，但保留配置
  provider: llamafactory

# ==================== 开发调试配置 ====================
logging:
  level:
    com.abin.mallchat.ai: DEBUG  # 输出 Mock 服务的详细日志
    com.abin.mallchat.common.chatai: DEBUG
```

### 4.2 启动命令

#### 方式一：命令行参数（推荐）

```bash
# Maven
mvn spring-boot:run -Dspring-boot.run.profiles=mock

# 或直接运行 JAR
java -jar mallchat-chat-server.jar --spring.profiles.active=mock
```

#### 方式二：修改 application.yml

```yaml
spring:
  profiles:
    active: mock  # 修改此处
```

#### 方式三：环境变量

```bash
export SPRING_PROFILES_ACTIVE=mock
java -jar mallchat-chat-server.jar
```

#### 方式四：IDEA 配置

```
Run → Edit Configurations → Active profiles: mock
```

### 4.3 切换真实服务

```yaml
# 切换到本地 Ollama + Qdrant 方案
spring:
  profiles:
    active: local  # 或删除 mock profile
```

```yaml
# application-local.yml
langchain4j:
  llm:
    provider: qwen-ollama  # 使用 Ollama 部署的 Qwen2.5-14B

ollama:
  base-url: http://localhost:11434
  model-name: qwen2.5:14b

embedding:
  provider: bge  # Ollama 部署的 bge-large-zh-v1.5

vector:
  store:
    provider: qdrant  # 本地 Qdrant 服务

qdrant:
  host: localhost
  port: 6334
```

---

## 5. 测试场景

### 5.1 单元测试（@Mock）

使用 Mockito 对单个组件进行隔离测试：

```java
@ExtendWith(MockitoExtension.class)
class RAGQueryTest {

    @Mock
    private LLMService llmService;  // Mock 的是接口，不是 MockLLMService

    @Mock
    private VectorService vectorService;

    @InjectMocks
    private RAGServiceImpl ragService;

    @Test
    void testRAGQuery_WithValidResults_ShouldReturnStreamingResponse() {
        // Given: 预设 Mock 行为
        when(embeddingService.generateEmbedding(anyString()))
                .thenReturn(new float[]{0.1f, 0.2f, 0.3f});

        when(vectorService.search(any(float[].class), anyInt(), anyLong()))
                .thenReturn(Arrays.asList(
                        SearchResult.builder().content("RAG是检索增强生成").score(0.95f).build()
                ));

        when(llmService.streamChat(anyString(), any(LLMOptions.class)))
                .thenReturn(Flux.just("RAG", "是", "一种", "技术"));

        // When: 执行测试
        Flux<String> result = ragService.ragQuery(request);

        // Then: 验证结果
        StepVerifier.create(result)
                .expectNext("RAG")
                .expectNext("是")
                .expectNext("一种")
                .expectNext("技术")
                .verifyComplete();
    }
}
```

**关键点**：
- `@Mock` 创建的是 Mockito 代理对象，不是 Spring Bean
- 适用于**单个组件**的单元测试
- 可以精确控制每个依赖的行为

### 5.2 集成测试（@SpringBootTest + @ActiveProfiles）

使用真实的 Spring 容器和 Mock 实现进行端到端测试：

```java
@Slf4j
@SpringBootTest
@ActiveProfiles("test")  // 激活 test profile（继承 mock profile）
@DisplayName("端到端 RAG 工作流测试")
public class EndToEndRAGWorkflowTest {

    @Autowired
    private RAGService ragService;  // 注入的是装配了 Mock 实现的 RAGService

    @Autowired
    private VectorService vectorService;  // 实际注入 MockVectorService

    @Test
    @DisplayName("完整 RAG 工作流")
    void testCompleteRAGWorkflow() throws InterruptedException {
        // Given: 准备测试文档
        String documentContent = "Spring Boot 是一个基于 Spring 框架的开源 Java 应用程序框架...";
        
        DocumentUploadRequest uploadRequest = DocumentUploadRequest.builder()
                .title("Spring Boot 技术文档")
                .content(documentContent)
                .documentType("txt")
                .uploadUserId(1001L)
                .build();

        // When: 上传文档 → MockVectorService 存储到内存
        Long documentId = ragService.uploadDocument(uploadRequest);

        // Then: 等待索引完成
        boolean indexReady = waitForIndexReady(documentId, Duration.ofSeconds(30));
        assertThat(indexReady).isTrue();

        // When: 执行 RAG 查询 → MockEmbeddingService 生成向量 → MockVectorService 检索 → MockLLMService 生成回复
        RAGQueryRequest queryRequest = RAGQueryRequest.builder()
                .question("Spring Boot 有什么特点？")
                .documentId(documentId)
                .build();

        Flux<String> responseStream = ragService.ragQuery(queryRequest);

        // Then: 验证流式响应
        StepVerifier.create(responseStream)
                .thenConsumeWhile(chunk -> {
                    log.debug("接收到响应块: {}", chunk);
                    return true;
                })
                .expectComplete()
                .verify(Duration.ofSeconds(30));
    }
}
```

**关键点**：
- `@ActiveProfiles("test")` 激活 test profile，通常 test profile 包含 mock
- 注入的是**真实的 Spring Bean**，使用 Mock 实现
- 验证完整的业务流程（上传 → 索引 → 查询 → 回答）
- 测试数据存储在内存中，测试结束后自动清理

### 5.3 属性测试（Property-Based Testing）

使用 jqwik 进行基于属性的测试，验证系统在各种输入下的行为：

```java
class ChunkSizeConstraintsPropertyTest {

    @Property(tries = 100)  // 随机生成 100 组测试数据
    @Label("分块大小应该在合理范围内")
    void chunkSizeShouldBeWithinBounds(
            @ForAll @StringLength(min = 1, max = 10000) String documentContent,
            @ForAll @IntRange(min = 100, max = 2000) int maxChunkSize) {

        // Given: 随机生成的文档内容和分块大小
        List<DocumentChunk> chunks = documentChunker.split(documentContent, maxChunkSize);

        // Then: 每个分块大小应该在合理范围内
        for (DocumentChunk chunk : chunks) {
            assertThat(chunk.getContent().length())
                    .isLessThanOrEqualTo(maxChunkSize);
        }
    }
}
```

**关键点**：
- 不预设具体输入，由框架随机生成
- 发现边界条件和隐蔽 bug
- 特别适合验证"对于所有 X，都应该满足 Y"的规格

### 5.4 Mock 在降级测试中的应用

验证系统在外部服务故障时的降级行为：

```java
class GracefulDegradationPropertyTest {

    @Mock
    private VectorService vectorService;

    @Mock
    private LLMService llmService;

    @Property(tries = 100)
    @Label("向量库失败时应该降级到普通问答")
    void vectorStoreFailureShouldDegradeGracefully(
            @ForAll @StringLength(min = 5, max = 200) String question) {

        // Given: 向量库抛出异常
        when(vectorService.search(any(), anyInt(), any()))
                .thenThrow(new VectorStoreException("Vector store unavailable"));

        // When: 调用降级服务
        when(llmService.streamChat(any(), any()))
                .thenReturn(Flux.just("这是降级后的回答"));

        Flux<String> result = degradationService.degradedRAGQuery(question);

        // Then: 应该返回降级响应，不抛出异常
        StepVerifier.create(result)
                .expectNextMatches(response -> response != null && !response.isEmpty())
                .verifyComplete();
    }
}
```

**关键点**：
- Mock 外部服务的异常行为
- 验证降级逻辑的正确性
- 确保用户体验（返回友好提示而非 500 错误）

---

## 6. 最佳实践

### 6.1 Mock 设计原则

#### ✅ 应该做的

| 原则 | 说明 | 本项目的实践 |
|------|------|------------|
| **实现完整接口** | Mock 必须实现接口的所有方法 | 4 个 Mock 都完整实现了对应接口 |
| **模拟真实行为** | 返回值格式、延迟、错误码与真实服务一致 | `MockLLMService` 模拟流式输出，`MockFineTuneClient` 模拟异步状态变化 |
| **确定性** | 相同输入产生相同输出 | `MockEmbeddingService` 使用 MD5 生成确定性向量 |
| **线程安全** | 支持并发访问 | `ConcurrentHashMap` 存储数据 |
| **可观测性** | 输出详细日志 | 所有 Mock 服务都使用 `@Slf4j` 记录操作 |
| **有状态** | 支持读写操作 | `MockVectorService` 存储数据，`MockFineTuneClient` 管理任务状态 |

#### ❌ 不应该做的

| 反模式 | 说明 |
|--------|------|
| **只返回 null** | Mock 应该有合理的返回值 |
| **硬编码大量数据** | 使用算法生成数据，保持灵活性 |
| **忽略异常路径** | 模拟错误场景（如网络超时） |
| **过度复杂** | Mock 不是完整实现，保持简单 |

### 6.2 常见问题与解决方案

#### 问题一：Mock Bean 没有生效

**现象**：激活 mock profile 后，仍然调用了真实服务。

**排查步骤**：

```bash
# 1. 检查 profile 是否正确激活
curl http://localhost:8080/actuator/env | grep "spring.profiles.active"

# 2. 查看启动日志，确认 Bean 注册
grep "Mock" logs/spring.log
# 应该看到: "MockLLMService initialized" 等日志

# 3. 检查 allow-bean-definition-overriding
# application-mock.yml 必须包含:
# spring.main.allow-bean-definition-overriding: true
```

#### 问题二：内存溢出（OOM）

**现象**：MockVectorService 存储大量数据后内存不足。

**解决方案**：

```java
// 添加内存限制和清理机制
@Component
@Profile("mock")
public class MockVectorService implements VectorService {
    
    private static final int MAX_DOCUMENTS = 1000;  // 限制文档数量
    private static final int MAX_CHUNKS_PER_DOC = 1000;  // 限制每文档分块数
    
    @Override
    public void storeVectors(Long documentId, List<DocumentChunk> chunks) {
        if (storage.size() >= MAX_DOCUMENTS) {
            log.warn("[Mock] Document limit reached ({})，removing oldest", MAX_DOCUMENTS);
            // LRU 清理策略
            removeOldestDocument();
        }
        // ...
    }
}
```

#### 问题三：多环境配置冲突

**现象**：mock profile 和其他 profile 同时激活，配置冲突。

**解决方案**：

```yaml
# application.yml
spring:
  profiles:
    group:
      mock: ai,mock      # mock 激活 ai 和 mock
      local: ai,local    # local 激活 ai 和 local
      test: test,mock    # test 激活 test 和 mock
    # 不要同时激活冲突的 profile
```

### 6.3 性能考虑

| 指标 | Mock 实现 | 真实实现 | 差距 |
|------|----------|---------|------|
| 向量生成 | ~1ms (MD5 + LCG) | ~100ms (Ollama BGE) | Mock 快 100x |
| 向量检索 | ~1ms (全表扫描，<1K 条) | ~5ms (HNSW, 百万级) | Mock 适合小数据 |
| LLM 调用 | ~10ms (字符流) | ~2000ms (Ollama Qwen2.5) | Mock 快 200x |
| 微调任务 | ~5s (模拟) | ~1h (真实训练) | Mock 仅演示 |

> 💡 **性能提示**：Mock 模式下的性能不代表真实性能，不要基于 Mock 做性能基准测试。

---

## 7. 面试高频问题

### Q1: 什么是 Spring Profile？你们项目怎么用的？

**参考答案**：

Spring Profile 是 Spring Boot 提供的**环境隔离**机制，允许同一套代码在不同环境下加载不同的 Bean。

在我们的 AI 模块中，使用 Profile 实现了**真实服务**和 **Mock 服务**的切换：

```java
@Service
@Profile("mock")
public class MockLLMService implements LLMService { }

@Service
@Profile("!mock")
public class OpenAILLMService implements LLMService { }
```

通过 `spring.profiles.active=mock` 激活 Mock 模式，无需部署 Ollama/Qdrant 即可启动项目，非常适合新成员 onboarding 和 CI/CD 测试。

### Q2: 你们为什么要自己写 Mock，不用 Mockito？

**参考答案**：

Mockito 的 `@Mock` 主要用于**单元测试**，创建的是接口的代理对象。而我们的 Mock 实现是** Fake（伪实现）**，有以下区别：

1. **生命周期不同**：Mockito Mock 在测试方法内创建和销毁；Fake 是 Spring Bean，随应用启动
2. **复杂度不同**：Mockito Mock 通常只预设返回值；Fake 有完整的工作实现（如余弦相似度计算）
3. **使用场景不同**：Mockito 用于单元测试隔离依赖；Fake 用于开发和集成测试

我们的 Fake 实现：
- `MockEmbeddingService` 实现了基于 MD5 的确定性向量生成算法
- `MockVectorService` 实现了基于内存的余弦相似度检索
- `MockFineTuneClient` 模拟了完整的异步任务状态流转

### Q3: 如何保证 Mock 和真实服务的行为一致？

**参考答案**：

三个层面的保证：

1. **接口约束**：Mock 和真实服务实现同一接口，方法签名完全一致
2. **行为模拟**：
   - `MockLLMService` 用 `Flux.fromArray(reply.split("")).delayElements(...)` 模拟流式输出
   - `MockEmbeddingService` 生成 1024 维单位向量，与 bge-large-zh-v1.5 一致
   - `MockVectorService` 使用标准的余弦相似度公式
3. **测试验证**：集成测试中，同一套测试用例分别在 Mock 模式和真实模式下执行，验证行为一致

### Q4: MockVectorService 的线程安全是怎么保证的？

**参考答案**：

使用 `ConcurrentHashMap` 作为存储：

```java
private final Map<Long, List<ChunkVector>> storage = new ConcurrentHashMap<>();
```

- `ConcurrentHashMap` 使用分段锁（Java 8+ 使用 CAS + synchronized），支持高并发读写
- 写操作（`storeVectors`、`deleteVectors`）使用 `put`/`remove`，线程安全
- 读操作（`search`、`exists`）使用 `get`/`containsKey`，无需加锁

> 追问：如果是真实场景（Qdrant/Milvus），它们如何保证并发？
> 
> Qdrant 使用 Rust 编写，基于 Actor 模型处理并发请求；Milvus 使用 gRPC + 分布式架构。

### Q5: 如果要在 Mock 模式下支持 10 万条向量检索，你会怎么优化？

**参考答案**：

当前 `MockVectorService` 使用全表扫描，时间复杂度 O(n)，适合小数据量（<1000 条）。如果要支持 10 万条：

1. **增加索引**：使用 KD-Tree 或 Ball-Tree 实现近似最近邻搜索，将复杂度降到 O(log n)
2. **并行化**：使用 Java Stream 并行流 `storage.entrySet().parallelStream()` 加速计算
3. **缓存热点**：对高频查询向量缓存 topK 结果
4. **向量压缩**：使用 PQ（Product Quantization）压缩向量，减少内存占用和计算量

但实际项目中，Mock 模式不需要支持大数据量，因为 Mock 的目的是**替代外部依赖进行开发测试**，不是性能测试。

### Q6: 你们的 Mock 实现属于测试金字塔的哪一层？

**参考答案**：

测试金字塔：

```
      /\
     /  \      E2E 测试（少）
    /____\
   /      \   集成测试（中）
  /________\
 /          \  单元测试（多）
/____________\
```

我们的 Mock 实现横跨三层：

1. **单元测试层**：使用 Mockito `@Mock` 创建接口代理，快速验证单个组件
2. **集成测试层**：使用 `@ActiveProfiles("test")` 激活 Mock Bean，验证组件间交互
3. **E2E 测试层**：端到端测试完整业务流程（上传 → 索引 → 查询 → 回答）

Mock 实现主要在**集成测试层**发挥作用，填补了"有 Spring 容器但无外部依赖"的测试空白。

---

## 8. 源码速查表

### 文件清单

| 文件 | 路径 | 说明 |
|------|------|------|
| MockLLMService | `mallchat-ai-llm/.../impl/MockLLMService.java` | LLM Mock |
| MockEmbeddingService | `mallchat-ai-vector/.../impl/MockEmbeddingService.java` | Embedding Mock |
| MockVectorService | `mallchat-ai-vector/.../impl/MockVectorService.java` | 向量存储 Mock |
| MockFineTuneClient | `mallchat-ai-finetune/.../MockFineTuneClient.java` | 微调服务 Mock |
| LLMService | `mallchat-ai-llm/.../service/LLMService.java` | LLM 接口 |
| EmbeddingService | `mallchat-ai-vector/.../service/EmbeddingService.java` | Embedding 接口 |
| VectorService | `mallchat-ai-vector/.../service/VectorService.java` | 向量存储接口 |
| FineTuneClient | `mallchat-ai-finetune/.../client/FineTuneClient.java` | 微调客户端 |
| LLMServiceFactory | `mallchat-ai-llm/.../service/LLMServiceFactory.java` | LLM 服务工厂 |
| application-mock.yml | `mallchat-chat-server/.../application-mock.yml` | Mock 配置 |

### 常用命令

```bash
# 启动 Mock 模式
mvn spring-boot:run -Dspring-boot.run.profiles=mock

# 运行 Mock 模式下的测试
mvn test -Dspring.profiles.active=mock

# 查看激活的 profile
curl http://localhost:8080/actuator/env | grep active

# 查看 Mock Bean 是否注册
grep "Mock" logs/spring.log
```

---

## 9. 扩展阅读

### 相关文档

| 文档 | 路径 | 内容 |
|------|------|------|
| AI 技术方案 | `mallchat-ai/docs/AI技术方案.md` | 完整技术选型、架构设计 |
| 项目文档总览 | `docs/项目文档总览.md` | 所有文档的导航索引 |
| 交付物清单 | `docs/交付物清单.md` | 项目交付物完整列表 |
| AI模块BadCase与面试谈资 | `mallchat-ai/docs/AI模块BadCase与面试谈资.md` | 12个真实技术case |

### 参考资源

- [Spring Boot Profiles 官方文档](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.profiles)
- [Mockito 官方文档](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [jqwik 属性测试框架](https://jqwik.net/)
- [余弦相似度 - Wikipedia](https://zh.wikipedia.org/wiki/%E4%BD%99%E5%BC%A6%E7%9B%B8%E4%BC%BC%E6%80%A7)
- [HNSW 算法论文](https://arxiv.org/abs/1603.09320)

---

> 📌 **文档维护**
> 
> - 作者：Claude Code (AI Assistant)
> - 最后更新：2026-06-01
> - 版本：v1.0
> - 关联学习记录：`.learnings/LEARNINGS.md` [LRN-20260601-002]
> 
> 如发现错误或需要补充，请更新本文档并同步修改学习记录。
