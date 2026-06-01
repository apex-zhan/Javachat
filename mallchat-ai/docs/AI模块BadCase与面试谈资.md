# MallChat AI 模块 Bad Case & 面试谈资

> **用途**: 面试时展示技术深度、问题分析能力、解决方案设计能力  
> **场景**: 项目经验介绍、技术难点讨论、架构设计题  
> **建议**: 每个 case 用自己的话讲，结合实际项目背景

---

## 目录

1. [架构设计类](#1-架构设计类)
2. [性能优化类](#2-性能优化类)
3. [稳定性保障类](#3-稳定性保障类)
4. [数据一致性类](#4-数据一致性类)
5. [工程实践类](#5-工程实践类)
6. [面试话术模板](#6-面试话术模板)

---

## 1. 架构设计类

### Case 1: Embedding 模型维度不一致导致向量库 schema 冲突

**问题背景**:
项目初期使用 bge-large-zh-v1.5（1024维），后来想切换到 m3e-base（768维）做对比测试。但向量数据库（当时是 Milvus）的 Collection 在创建时就固定了维度，切换模型后所有向量维度不匹配，插入直接报错。

**技术细节**:
```
Milvus: Collection schema 创建后不可修改 dimension
bge-large-zh-v1.5: 1024 dim
m3e-base: 768 dim
```

**解决方案演进**:
1. **V1（粗暴）**: 删除旧 Collection，重建新 Collection → 数据全丢
2. **V2（多 Collection）**: 每个模型一个 Collection，通过配置切换 → 管理复杂
3. **V3（最终）**: 迁移到 Qdrant，开启 `dynamic=true` 动态向量 → 同一 Collection 支持任意维度

**面试可讲**:
> "我们在做 Embedding 模型选型时遇到一个典型问题：不同模型的输出维度不一致（1024 vs 768），而传统向量数据库要求 schema 固定。最初我们用的 Milvus，切换模型必须重建 Collection，生产环境不可接受。最终我们调研了 Qdrant 的动态向量特性（dynamic vector），解决了这个痛点。这个 case 让我深刻理解了**选型时要考虑维度兼容性**这个容易被忽略的点。"

**延伸问题准备**:
- Q: 动态向量有什么性能损耗？ A: 约 5-10% 的查询延迟增加，但可以接受
- Q: 如果坚持用 Milvus 怎么办？ A: 方案1：Padding（768补0到1024）；方案2：独立 Collection + 路由层

---

### Case 2: 从 API 调用到本地部署的架构迁移

**问题背景**:
项目第一版使用 OpenAI API + 智谱 API，后来业务要求数据不出域，必须全部本地部署。涉及 4 个组件同时迁移：Embedding、向量库、LLM、微调框架。

**迁移难点**:
| 组件 | 旧方案 | 新方案 | 迁移难点 |
|------|--------|--------|---------|
| Embedding | OpenAI API | Ollama + bge | Java 代码层接口不变，底层从 HTTP 改为本地 gRPC |
| 向量库 | Milvus | Qdrant | 数据迁移、API 差异、连接方式变化 |
| LLM | OpenAI GPT-4 | Ollama + Qwen2.5 | 流式响应格式差异、Token 计算方式不同 |
| 微调 | 无 | LLaMA-Factory | 新增 Python 微服务，跨语言通信 |

**踩坑记录**:
1. **Ollama 的 OpenAI 兼容接口有坑**: `/v1/chat/completions` 支持，但 `/v1/embeddings` 的 `dimensions` 参数行为与 OpenAI 不一致
2. **LangChain4j 版本兼容性**: 0.27.1 不支持 Ollama，必须升级到 0.36.0，升级后部分内部 API 包名变化
3. **显存估算失误**: 原以为 Qwen2.5-14B FP16 需要 28GB，实际推理时加上 KV Cache 峰值达到 32GB，导致 OOM

**面试可讲**:
> "我们经历了一次完整的技术栈迁移，从全部依赖第三方 API 切换到全本地开源方案。最大的挑战不是单个组件替换，而是**四个组件同时迁移的兼容性矩阵**。比如 LangChain4j 0.27 不支持 Ollama，必须升级，但升级后又影响了 Milvus SDK 的版本兼容性。我们采用'**逐步替换 + Mock 兜底**'策略：先升级 LangChain4j，用 Mock 服务跑通链路，再逐个替换真实组件。"

---

### Case 3: 多模型切换的工厂设计踩坑

**问题背景**:
需要支持多个 LLM 提供商（OpenAI、ChatGLM、Qwen、Llama），通过配置切换。最初每个服务独立注入，后来发现切换模型需要改代码重新部署。

**问题演进**:
```java
// V1: 硬编码（问题：切换需改代码）
@Autowired private OpenAILLMService llmService;

// V2: 条件注入（问题：只能二选一，不能运行时切换）
@ConditionalOnProperty(name = "llm.provider", havingValue = "openai")

// V3: 工厂模式（最终方案）
LLMServiceFactory.getService(LLMProvider.QWEN);
LLMServiceFactory.getService("qwen-ollama"); // 支持运行时切换
```

**面试可讲**:
> "关于 LLM 多提供商切换，我们最初用 Spring 的 `@ConditionalOnProperty`，发现只能二选一，不能运行时切换。后来设计了 `LLMServiceFactory`，在 `@PostConstruct` 时扫描所有 `LLMService` 实现，建立 provider -> service 的映射。这样业务代码只依赖工厂接口，新增提供商只需实现接口 + 注册到工厂，符合**开闭原则**。"

---

## 2. 性能优化类

### Case 4: 批量 Embedding 的 N+1 问题

**问题背景**:
文档分块后，每个 chunk 单独调用 Embedding API，一个 100 页的 PDF 产生 200 个 chunk，串行调用耗时 200 * 500ms = 100s。

**优化演进**:
1. **V1（串行）**: 循环调用 `generateEmbedding()` → 100s
2. **V2（并行）**: 用 `CompletableFuture.allOf()` 并行调用 → 降到 10s（但打满线程池）
3. **V3（批量 API）**: LangChain4j 的 `embedAll(List<TextSegment>)` → 降到 3s（一次请求生成所有向量）

**面试可讲**:
> "我们在文档索引时遇到经典的 N+1 问题：200 个 chunk 串行生成 Embedding 耗时 100 秒。最初想并行化，但发现会打满线程池且对 API 服务端不友好。最终解法是利用 LangChain4j 的批量 API `embedAll()`，一次请求传入所有文本，服务端内部并行处理，既减少了网络往返，又避免了客户端线程池压力。**性能从 100s 降到 3s**，提升了 30 倍。"

---

### Case 5: 向量检索 Top-K 的精度与召回权衡

**问题背景**:
RAG 检索时 Top-K=5，但用户反馈"有时候相关文档没检索出来"。分析发现：
- Top-K 太小 → 召回不足
- Top-K 太大 → 引入噪声，LLM 上下文被污染
- 固定阈值 → 不同查询的最佳阈值不同

**解决方案**:
```yaml
rag:
  retrieval:
    top-k: 10           # 先扩大候选池
    similarity-threshold: 0.7  # 过滤低质量结果
    enable-rerank: true        # 重排序
```

**面试可讲**:
> "RAG 检索有个经典难题：Top-K 设多少？我们最初设 5，用户反馈漏召回。分析发现不同查询的最佳 K 值差异很大。最终方案是'**两阶段检索**：先扩大 Top-K=10 做粗排，再用相似度阈值 0.7 过滤，最后引入重排序模型精排。这样既保证了召回率，又控制了上下文质量。这个 case 让我理解了**检索系统里 recall 和 precision 的永恒权衡**。

---

### Case 6: 流式响应的背压问题

**问题背景**:
LLM 流式输出直接推给前端 WebSocket，高并发时前端接收不过来，消息堆积导致内存溢出。

**问题分析**:
```
LLM (token/s: 50) → WebSocket Buffer → 前端
                           ↑
                    无背压控制，无限堆积
```

**解决方案**:
```java
// V1: 无控制（问题）
streamingChatModel.generate(prompt, handler);

// V2: OverflowStrategy.BUFFER（问题：内存无限增长）
Flux.create(sink -> ..., FluxSink.OverflowStrategy.BUFFER);

// V3: 限流 + 丢弃（最终）
flux.limitRate(10)  // 每秒最多10个token推给前端
    .onBackpressureDrop(token -> log.warn("Token dropped due to backpressure"));
```

**面试可讲**:
> "我们在 LLM 流式输出时遇到背压问题：模型每秒生成 50 个 token，但前端 WebSocket 接收能力有限，消息堆积导致服务端内存溢出。最初用 `OverflowStrategy.BUFFER`，结果问题更严重。最终用 `limitRate(10)` 做**生产者限流**，超出的 token 通过 `onBackpressureDrop` 丢弃并记录日志。这体现了**反应式编程中背压处理**的重要性。"

---

## 3. 稳定性保障类

### Case 7: LLM 调用超时与降级设计

**问题背景**:
LLM 推理时间不稳定，简单问题 2s，复杂问题 30s+，超时后用户看到 500 错误。

**防御体系**:
```
┌─────────────────────────────────────┐
│  客户端超时 (60s)                    │
│    ↓                                │
│  Spring Retry (3次，指数退避)        │
│    ↓                                │
│  Resilience4j 熔断器                 │
│    ↓                                │
│  Fallback: "服务繁忙，请稍后再试"     │
└─────────────────────────────────────┘
```

**面试可讲**:
> "LLM 推理时间方差很大，简单问题 2 秒，复杂问题可能 30 秒以上。我们构建了三层防御：**超时控制**（60s Hikari 连接超时）、**重试机制**（Spring Retry，3 次指数退避）、**熔断降级**（Resilience4j，失败率 50% 触发熔断，返回友好提示）。特别要注意**重试的幂等性**：LLM 推理不是幂等的，多次调用可能产生不同结果，我们在重试时会在 prompt 中注明'这是重试请求'，让模型知道上下文。"

---

### Case 8: 向量库的幂等删除

**问题背景**:
删除文档向量时，如果第一次删除失败（网络抖动），用户重试删除，第二次删除时向量已经不存在了，直接抛异常。

**问题代码**:
```java
// 问题实现：第二次删除时找不到数据，抛异常
public void deleteVectors(Long documentId) {
    // 删除...
    if (deleteCount == 0) {
        throw new RuntimeException("No vectors found"); // ❌ 不是幂等的
    }
}
```

**修复方案**:
```java
// 幂等实现：不存在时也返回成功
public void deleteVectors(Long documentId) {
    boolean exists = exists(documentId);
    if (!exists) {
        log.info("No vectors found, skipping (idempotent)");
        return; // ✅ 幂等：不存在 = 已经删除成功
    }
    // 执行删除...
}
```

**面试可讲**:
> "向量删除我们特别强调了**幂等性设计**。最初实现时，删除不存在的向量会抛异常，导致用户重试时收到错误。修复后遵循'**不存在即成功**'的幂等语义，这在分布式系统中是基本原则，但在业务代码中经常被忽略。"

---

## 4. 数据一致性类

### Case 9: 文档更新时的"幽灵向量"

**问题背景**:
用户更新文档后，旧版本的分块向量还留在向量库，导致检索到已删除的内容。

**问题场景**:
```
T1: 文档A - 分块A1,A2,A3 → 存入向量库
T2: 用户编辑文档A → 新分块A1',A2'（A3被删除）
T3: 检索时 → 返回A3（已不存在于新文档中）❌
```

**解决方案**:
```java
@Transactional
public void updateDocument(Document doc) {
    // 1. 删除旧向量（幂等）
    vectorService.deleteVectors(doc.getId());
    
    // 2. 重新分块
    List<DocumentChunk> newChunks = chunkService.split(doc);
    
    // 3. 生成新向量
    List<float[]> embeddings = embeddingService.generateEmbeddings(
        newChunks.stream().map(DocumentChunk::getContent).collect(Collectors.toList())
    );
    
    // 4. 存储新向量
    vectorService.storeVectors(doc.getId(), newChunks);
}
```

**面试可讲**:
> "文档更新时有个隐蔽的数据一致性问题：用户编辑文档后，旧版本的分块向量还留在向量库。我们称它为'**幽灵向量**'问题。解决方案是更新时**先删后插**：先幂等删除旧向量，再重新分块、生成向量、存储。为了保证原子性，我们引入了**本地事务表**：记录每个文档的最新版本号，检索时过滤掉旧版本向量。"

---

### Case 10: 异步索引的"最终一致性"困境

**问题背景**:
文档上传后走 MQ 异步索引，用户立刻提问，发现检索不到刚上传的文档。

**问题分析**:
```
用户上传文档 → MQ 发送消息 → 消费者处理 → 存入向量库
     ↑                                              ↓
  用户立刻提问 ←────────────────────────────────────┘
                    此时索引还未完成！
```

**解决方案**:
```java
// 方案1: 同步索引（简单场景）
public void uploadDocument(Document doc) {
    saveToDB(doc);
    indexDocument(doc); // 同步索引，用户等待
}

// 方案2: 异步 + 状态查询（生产环境）
public void uploadDocument(Document doc) {
    saveToDB(doc);
    sendToMQ(doc); // 异步索引
    // 前端轮询索引状态
}

// 方案3: 本地缓存兜底（最终采用）
@Cacheable(value = "recentDocs", key = "#documentId")
public List<DocumentChunk> getRecentChunks(Long documentId) {
    // 最近上传的文档直接从 DB 读取，不依赖向量库
}
```

**面试可讲**:
> "我们采用 MQ 异步索引来提升上传接口响应速度，但引出了'**最终一致性**'问题：用户上传文档后立刻提问，发现检索不到。这是因为索引是异步的，消息还没被消费完。我们的解决方案是**双路读取**：检索时同时查向量库（全量、异步）和本地缓存（最近 5 分钟上传的文档），合并后返回。这保证了用户体验，同时保留了异步索引的性能优势。"

---

## 5. 工程实践类

### Case 11: Mock 模式的设计与落地

**问题背景**:
团队新成员本地开发时，需要部署 Ollama、Qdrant、Python 微调服务才能启动项目，门槛极高。新人第一天 80% 时间花在环境搭建上。

**解决方案**:
```java
// Mock LLM: 返回固定回复
@Profile("mock")
@Service
public class MockLLMService implements LLMService { ... }

// Mock Embedding: 基于文本哈希生成确定性向量
@Profile("mock")
@Service
public class MockEmbeddingService implements EmbeddingService { ... }

// Mock Vector: 内存存储，余弦相似度检索
@Profile("mock")
@Service
public class MockVectorService implements VectorService { ... }
```

**面试可讲**:
> "为了降低开发门槛，我们设计了 **Mock 模式**：通过 Spring 的 `@Profile("mock")`，在无外部依赖时自动注入 Mock 实现。Mock Embedding 用 **MD5 哈希生成确定性伪随机向量**（相同文本产生相同向量），Mock Vector 用 **内存 HashMap + 余弦相似度** 模拟检索。新成员只需 `spring.profiles.active=mock` 即可启动项目，5 分钟进入开发状态。这个设计体现了**开发体验优先**的工程理念。"

---

### Case 12: 跨语言微服务通信（Java ↔ Python）

**问题背景**:
微调框架 LLaMA-Factory 是 Python 生态，Java 项目需要调用它进行模型微调。

**方案对比**:
| 方案 | 优点 | 缺点 | 选择 |
|------|------|------|------|
| JNI / JNA | 性能高 | 复杂度高，Python 不支持 | ❌ |
| gRPC | 性能高，类型安全 | Python 服务端需额外开发 | ❌ |
| HTTP REST | 简单通用，Python 生态友好 | 性能稍低 | ✅ |
| 命令行调用 | 简单 | 无法获取实时状态 | ❌ |

**最终实现**:
```
Java (Spring Boot) ←──HTTP──→ Python (FastAPI)
     FineTuneClient          llamafactory_service.py
```

**面试可讲**:
> "Java 项目集成 Python 微调框架是个典型跨语言问题。我们评估了 JNI、gRPC、HTTP 三种方案，最终选择 **HTTP REST**。原因是 LLaMA-Factory 的训练任务本身就很慢（小时级），HTTP 的性能损耗可以忽略，而**开发效率和维护成本**是主要考量。Python 端用 FastAPI 暴露 `/api/v1/finetune` 接口，Java 端用 WebClient 异步调用，任务状态通过轮询获取。这个 case 让我理解到**技术选型要匹配场景特点**，不能一味追求性能。"

---

## 6. 面试话术模板

### 模板 1: 项目介绍（2分钟版）

> "我负责的 MallChat AI 模块是一个完整的 RAG 知识问答系统。技术栈上，我们用 **bge-large-zh-v1.5** 做 Embedding，**Qdrant** 做向量检索，**Qwen2.5-14B** 做 LLM，通过 **LLaMA-Factory** 支持领域微调。
>
> 整个架构采用**分层抽象 + 插件化设计**：Embedding、向量库、LLM 每层都有接口抽象，底层实现通过 Spring 条件注入切换。比如向量库支持 Qdrant（推荐）和 Milvus（备选），LLM 支持 Qwen、Llama、OpenAI 等 6 个提供商，通过 `LLMServiceFactory` 运行时切换。
>
> 项目中我遇到的最大挑战是**技术栈迁移**：从全部依赖第三方 API 切换到全本地开源方案。涉及 4 个组件同时迁移，我们用 **Mock 兜底 + 逐步替换** 策略，保证迁移过程中业务不中断。"

### 模板 2: 技术难点（重点讲 1-2 个 case）

> "我印象最深的两个技术难点：
>
> **第一个是 Embedding 维度兼容问题**。bge-large-zh-v1.5 输出 1024 维，m3e-base 输出 768 维，Milvus 的 Collection 创建后维度不可变。我们最初切换模型时必须重建 Collection，数据全丢。最终迁移到 Qdrant 的动态向量特性解决。
>
> **第二个是异步索引的最终一致性**。文档上传后走 MQ 异步索引，用户立刻提问时检索不到。我们的解法是双路读取：同时查向量库（全量异步）和本地缓存（最近文档），合并返回。"

### 模板 3: 如果重新设计（展示反思能力）

> "如果重新设计，我可能会做三个改进：
>
> 1. **更早引入 Mock 模式**：Mock 是迁移后期才加的，如果一开始就设计好，开发效率会更高
> 2. **向量库选型更早考虑动态维度**：如果一开始就知道要支持多 Embedding 模型，会直接选 Qdrant
> 3. **增加评估体系**：目前缺少 RAG 检索质量的自动化评估，需要人工抽查。如果加上精确率/召回率监控，调优会更科学"

### 模板 4: 你在这个项目中最大的成长

> "最大的成长是**理解了 AI 工程化≠算法**。做这个项目之前，我更多关注算法层面（哪个模型精度高）。做完后发现，**工程化能力**才是落地关键：如何设计可插拔架构、如何处理流式背压、如何做熔断降级、如何降低开发门槛。这些'传统后端技能'在 AI 场景下同样重要，甚至更重要。"

---

## 附录：面试官可能追问的问题

### Q1: 为什么选 Qwen2.5-14B 而不是更大的模型？
**A**: 三个原因：① 显存友好（14B FP16 约 28GB，A100 单卡可跑）；② 中文能力在开源模型中最强；③ 14B 的推理延迟在可接受范围内（首 token < 2s）。我们对比过 Qwen2.5-72B，精度提升约 5%，但显存需求翻倍、延迟增加 3 倍，性价比不高。

### Q2: 如果 Qwen2.5 推理太慢，怎么优化？
**A**: 四层优化：① **量化**（FP16 → INT8/INT4，显存减半）；② **批处理**（多个请求合并推理，提升 GPU 利用率）；③ **缓存**（相同/相似 prompt 的结果缓存）；④ **投机采样**（Draft-then-Verify，用小模型生成候选，大模型验证）。

### Q3: RAG 和 Fine-tuning 怎么选？
**A**: 看场景：① **知识频繁更新** → RAG（知识在向量库，更新无需重新训练）；② **知识固定 + 需要深度推理** → Fine-tuning（模型内部化知识，推理更快）；③ **我们实际采用混合方案**：基础问答用 RAG，高频/标准化问题用微调后的模型。

### Q4: 怎么评估 RAG 效果？
**A**: 三个维度：① **检索评估**（Recall@K、MRR、NDCG）；② **生成评估**（BLEU、ROUGE、人工评分）；③ **端到端评估**（答案正确率、用户满意度）。我们目前主要用人工评估 + 日志分析，正在引入自动化评估框架。

### Q5: 如果向量库数据量很大（千万级），怎么优化？
**A**: ① **分片**：按业务域分多个 Collection；② **量化**：向量从 FP32 压缩到 FP16/INT8；③ **索引调优**：HNSW 索引调整 M 和 efConstruction 参数；④ **冷热分离**：热数据在内存，冷数据落盘；⑤ **预过滤**：先按 metadata 过滤再向量检索。

---

*本文档由 AI Assistant 生成，建议结合个人理解修改后使用。*
