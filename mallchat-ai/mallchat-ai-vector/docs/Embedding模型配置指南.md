# Embedding 模型配置指南

## 📋 文档信息

- **版本**：v1.1
- **创建时间**：2026-05-28
- **最后更新**：2026-06-13
- **作者**：Kiro / AI Assistant
- **状态**：✅ 已随代码迁移到 Ollama 方案更新

---

## 🎯 概述

本文档详细说明 MallChat AI 系统中 Embedding 模型的选型、配置和使用方法。Embedding 模型是 RAG（检索增强生成）系统的核心组件，负责将文本转换为向量表示，直接影响检索质量和系统性能。

---

## 🔍 模型选型

### 当前配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| 模型名称 | bge-large-zh-v1.5 | 推荐模型 |
| 向量维度 | 1024 | bge-large-zh-v1.5 |
| 备选模型 | m3e-base | 768 维，资源受限场景 |
| 部署方式 | 本地部署（Ollama） | 推荐 |
| 框架 | LangChain4j 0.36.0 | Java 集成 |

### 实现列表

| 实现类 | 模型 | 维度 | 启用条件 |
|--------|------|------|----------|
| `OllamaBgeEmbeddingService` | bge-large-zh-v1.5 | 1024 | `embedding.provider=bge`（默认，非 mock） |
| `M3eEmbeddingService` | m3e-base | 768 | `embedding.provider=m3e` |
| `OpenAIEmbeddingService` | OpenAI 兼容模型 | 可配置 | `embedding.provider=openai` |
| `MockEmbeddingService` | 确定性伪随机向量 | 1024（默认） | `spring.profiles.active=mock` |

### 选型分析

#### bge-large-zh-v1.5 优势

| 维度 | 说明 | 评分 |
|------|------|------|
| 中文效果 | 专为中文语料训练，语义检索效果优秀 | ⭐⭐⭐⭐⭐ |
| 向量维度 | 1024 维，在效果和效率间取得平衡 | ⭐⭐⭐⭐⭐ |
| 本地部署 | 支持 Ollama 一键部署 | ⭐⭐⭐⭐⭐ |
| 成本 | 开源免费，无 API 调用费用 | ⭐⭐⭐⭐⭐ |
| 社区支持 | 活跃的开源社区，持续更新 | ⭐⭐⭐⭐ |

#### 与其他模型对比

| 模型 | 维度 | 中文效果 | 本地部署 | 成本 | 推荐场景 |
|------|------|---------|---------|------|----------|
| **bge-large-zh-v1.5** | 1024 | ⭐⭐⭐⭐⭐ | ✅ | 免费 | **中文 RAG 系统（推荐）** |
| m3e-base | 768 | ⭐⭐⭐⭐ | ✅ | 免费 | 资源受限场景 |
| bge-m3 | 1024 | ⭐⭐⭐⭐ | ✅ | 免费 | 多语言场景 |
| bge-small-zh | 512 | ⭐⭐⭐ | ✅ | 免费 | 资源受限场景 |
| text-embedding-ada-002 | 1536 | ⭐⭐⭐ | ❌ | 收费 | OpenAI 生态 |
| text-embedding-3-small | 1536 | ⭐⭐⭐⭐ | ❌ | 收费 | OpenAI 生态 |
| text-embedding-3-large | 3072 | ⭐⭐⭐⭐⭐ | ❌ | 收费 | 高精度需求 |

### 为什么选择 bge-large-zh-v1.5

1. **中文优化**：MallChat 是中文系统，bge-large-zh-v1.5 专为中文设计
2. **成本优势**：本地部署无 API 调用费用，适合生产环境
3. **效果优秀**：在 MTEB 中文检索任务中排名前列
4. **效率平衡**：1024 维度在检索精度和存储/计算成本间取得平衡
5. **部署灵活**：支持 Ollama 一键部署

---

## ⚙️ 配置说明

### 配置文件

配置文件位置：`mallchat-chat-server/src/main/resources/application-ai.yml` 或 `application-local.yml`

```yaml
# ==================== Embedding 配置 ====================
embedding:
  provider: bge   # 推荐: bge | 备选: m3e | 兼容: openai | mock

ollama:
  base-url: http://localhost:11434
  embedding-model: bge-large-zh-v1.5
  timeout: 60s
  max-retries: 3
```

### OllamaBgeEmbeddingService 配置项

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `ollama.base-url` | `http://localhost:11434` | Ollama 服务地址 |
| `ollama.embedding-model` | `bge-large-zh-v1.5` | Embedding 模型名称 |
| `ollama.timeout` | `60s` | 请求超时 |
| `ollama.max-retries` | `3` | 最大重试次数 |

### M3eEmbeddingService 配置

```yaml
embedding:
  provider: m3e

ollama:
  base-url: http://localhost:11434
  embedding-model: m3e
  timeout: 60s
  max-retries: 3
```

### OpenAI Embedding 兼容配置

```yaml
embedding:
  provider: openai

langchain4j:
  openai:
    api-key: sk-xxx
    base-url: https://api.openai.com/v1
    embedding-model:
      model-name: text-embedding-3-large
```

### Mock 模式配置

```yaml
spring:
  profiles:
    active: mock

embedding:
  provider: mock
```

### 向量库配置

#### Qdrant（推荐，动态向量）

```yaml
vector:
  store:
    provider: qdrant

qdrant:
  host: localhost
  port: 6334
  collection-name: mallchat_knowledge
  grpc-timeout: 30
  use-tls: false
```

**注意**：Qdrant 支持动态向量，切换 bge(1024) / m3e(768) 无需重建 Collection。

#### Milvus（备选，固定维度）

```yaml
vector:
  store:
    provider: milvus

milvus:
  host: localhost
  port: 19530
  database: default
  collection:
    name: mallchat_knowledge_vectors
    # 向量维度必须与 Embedding 模型一致
    dimension: 1024
    metric-type: COSINE
```

**注意**：Milvus 需要固定维度，切换模型时必须重建 Collection。

### 环境变量配置

| 环境变量 | 说明 | 默认值 |
|----------|------|--------|
| `OLLAMA_BASE_URL` | Ollama 服务地址 | `http://localhost:11434` |
| `QDRANT_HOST` | Qdrant 主机 | `localhost` |
| `QDRANT_PORT` | Qdrant gRPC 端口 | `6334` |
| `OPENAI_API_KEY` | OpenAI API 密钥 | - |

---

## 🚀 部署指南

### 方案一：Ollama 部署（推荐）

#### 1. 安装 Ollama

```bash
# macOS/Linux
curl -fsSL https://ollama.com/install.sh | sh

# Windows
# 从 https://ollama.com/download 下载安装包
```

#### 2. 拉取模型

```bash
# 拉取 bge-large-zh-v1.5 模型
ollama pull bge-large-zh-v1.5

# 拉取 m3e-base 模型（备选）
ollama pull m3e

# 验证模型
ollama list
```

#### 3. 启动服务

```bash
# 启动 Ollama 服务（默认端口 11434）
ollama serve

# 验证服务
curl http://localhost:11434/api/tags
```

#### 4. 测试 Embedding

```bash
curl http://localhost:11434/api/embeddings \
  -H "Content-Type: application/json" \
  -d '{
    "model": "bge-large-zh-v1.5",
    "prompt": "这是一段测试文本"
  }'
```

#### 5. 应用配置

```yaml
embedding:
  provider: bge

ollama:
  base-url: http://localhost:11434
  embedding-model: bge-large-zh-v1.5
  timeout: 60s
  max-retries: 3
```

### 方案二：Docker 部署 Ollama

```bash
# 拉取镜像
docker pull ollama/ollama

# 启动容器（GPU）
docker run -d \
  --name ollama \
  -p 11434:11434 \
  -v ollama_data:/root/.ollama \
  --gpus all \
  ollama/ollama

# 进入容器拉取模型
docker exec -it ollama ollama pull bge-large-zh-v1.5
```

---

## 💻 代码实现

### 服务接口

```java
public interface EmbeddingService {
    /**
     * 生成单个文本的向量
     * @param text 文本内容
     * @return 向量数组（1024 维或 768 维）
     */
    float[] generateEmbedding(String text);
    
    /**
     * 批量生成文本向量
     * @param texts 文本列表
     * @return 向量列表
     */
    List<float[]> generateEmbeddings(List<String> texts);
}
```

### OllamaBgeEmbeddingService 实现

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

    @Value("${ollama.timeout:60s}")
    private Duration timeout;

    @Value("${ollama.max-retries:3}")
    private Integer maxRetries;

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

### MockEmbeddingService 实现

```java
@Slf4j
@Service
@Profile("mock")
public class MockEmbeddingService implements EmbeddingService {

    private static final int DEFAULT_DIMENSION = 1024;

    @Override
    public float[] generateEmbedding(String text) {
        return generateDeterministicVector(text, DEFAULT_DIMENSION);
    }

    @Override
    public List<float[]> generateEmbeddings(List<String> texts) {
        return texts.stream()
                .map(this::generateEmbedding)
                .collect(Collectors.toList());
    }

    private float[] generateDeterministicVector(String text, int dimension) {
        // 基于 MD5 生成确定性伪随机向量
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
        
        float[] vector = new float[dimension];
        // ... L2 归一化
        return vector;
    }
}
```

---

## 📊 性能指标

### bge-large-zh-v1.5 性能数据

| 指标 | 数值 | 说明 |
|------|------|------|
| 向量维度 | 1024 | 存储空间约 4KB/向量 |
| 推理速度 | ~20ms/文本 | GPU 环境（RTX 3090） |
| 批量推理 | ~100ms/100文本 | GPU 环境（RTX 3090） |
| 模型大小 | ~1.3GB | FP16 精度 |
| MTEB 中文检索排名 | Top 5 | C-MTEB 榜单 |

### m3e-base 性能数据

| 指标 | 数值 | 说明 |
|------|------|------|
| 向量维度 | 768 | 存储空间约 3KB/向量 |
| 推理速度 | ~10ms/文本 | GPU 环境（RTX 3090） |
| 模型大小 | ~400MB | FP16 精度 |

### 存储估算

| 文档数量 | 分块数量（平均 10/文档） | 向量存储空间（1024维） | 向量存储空间（768维） |
|----------|-------------------------|----------------------|----------------------|
| 1,000 | 10,000 | ~40MB | ~30MB |
| 10,000 | 100,000 | ~400MB | ~300MB |
| 100,000 | 1,000,000 | ~4GB | ~3GB |
| 1,000,000 | 10,000,000 | ~40GB | ~30GB |

---

## 🔧 常见问题

### Q1: 如何验证模型是否正确加载？

```java
@Test
public void testEmbeddingDimension() {
    float[] embedding = embeddingService.generateEmbedding("测试文本");
    assertEquals(1024, embedding.length);  // bge
    // assertEquals(768, embedding.length);  // m3e
}
```

### Q2: 向量维度不匹配怎么办？

1. 如果使用 **Qdrant**：无需处理，动态向量会自动适配
2. 如果使用 **Milvus**：需要重建 Collection
   ```bash
   # 删除旧 Collection
   # 修改 milvus.collection.dimension 为新的维度
   # 重新索引文档
   ```

### Q3: Ollama 服务无法连接？

```bash
# 检查服务状态
curl http://localhost:11434/api/tags

# 检查端口占用
netstat -an | grep 11434

# 重启服务
ollama serve
```

### Q4: 如何切换到其他模型？

1. 修改 `embedding.provider`
2. 修改 `ollama.embedding-model`
3. 如果使用 Milvus，同步修改 `milvus.collection.dimension`
4. 如果使用 Qdrant，无需修改向量库配置

```yaml
# 切换到 m3e-base
embedding:
  provider: m3e
ollama:
  embedding-model: m3e
```

### Q5: 批量 Embedding 时内存溢出？

分批处理，每批不超过 100 条：

```java
List<float[]> allEmbeddings = new ArrayList<>();
int batchSize = 100;

for (int i = 0; i < texts.size(); i += batchSize) {
    List<String> batch = texts.subList(i, Math.min(i + batchSize, texts.size()));
    allEmbeddings.addAll(embeddingService.generateEmbeddings(batch));
}
```

### Q6: 为什么 Mock 模式下相同文本向量相同？

MockEmbeddingService 使用 **MD5 哈希**生成确定性伪随机向量，保证相同文本产生相同向量，从而保证 Mock 模式下语义检索的一致性。

---

## 📚 参考资料

### 官方资源

- [BGE 模型 GitHub](https://github.com/FlagOpen/FlagEmbedding)
- [Ollama 官方文档](https://ollama.com/docs)
- [LangChain4j 文档](https://docs.langchain4j.dev/)

### 技术论文

- [C-Pack: Packaged Resources to Advance General Chinese Embedding](https://arxiv.org/abs/2309.07597)
- [MTEB: Massive Text Embedding Benchmark](https://arxiv.org/abs/2210.07316)

### 相关文档

- [AI技术方案](../AI技术方案.md)
- [架构设计详解](../../mallchat-ai-rag/docs/架构设计详解.md)
- [部署运维指南](../../mallchat-ai-rag/docs/部署运维指南.md)
- [API 接口文档](../../mallchat-ai-rag/docs/API接口文档.md)

---

## 📝 更新日志

| 日期 | 版本 | 更新内容 | 更新人 |
|------|------|---------|--------|
| 2026-06-13 | v1.1 | 迁移到 Ollama 部署方案；增加 m3e、Mock、Qdrant 动态向量说明 | AI Assistant |
| 2026-05-28 | v1.0 | 创建 Embedding 模型配置指南 | Kiro |

---

**维护者**：RAG 开发团队
