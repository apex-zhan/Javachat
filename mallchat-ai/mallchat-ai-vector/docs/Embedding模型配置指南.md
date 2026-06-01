# Embedding 模型配置指南

## 📋 文档信息

- **版本**：v1.0
- **创建时间**：2026-05-28
- **最后更新**：2026-05-28
- **作者**：Kiro
- **状态**：✅ 已完成

---

## 🎯 概述

本文档详细说明 MallChat AI 系统中 Embedding 模型的选型、配置和使用方法。Embedding 模型是 RAG（检索增强生成）系统的核心组件，负责将文本转换为向量表示，直接影响检索质量和系统性能。

---

## 🔍 模型选型

### 当前配置

| 配置项 | 值 |
|--------|-----|
| 模型名称 | bge-large-zh-v1.5 |
| 向量维度 | 1024 |
| 部署方式 | 本地部署（Ollama/vLLM） |
| API 协议 | OpenAI 兼容 API |

### 选型分析

#### bge-large-zh-v1.5 优势

| 维度 | 说明 | 评分 |
|------|------|------|
| 中文效果 | 专为中文语料训练，语义检索效果优秀 | ⭐⭐⭐⭐⭐ |
| 向量维度 | 1024 维，在效果和效率间取得平衡 | ⭐⭐⭐⭐⭐ |
| 本地部署 | 支持 Ollama、vLLM 等主流框架 | ⭐⭐⭐⭐⭐ |
| 成本 | 开源免费，无 API 调用费用 | ⭐⭐⭐⭐⭐ |
| 社区支持 | 活跃的开源社区，持续更新 | ⭐⭐⭐⭐ |

#### 与其他模型对比

| 模型 | 维度 | 中文效果 | 本地部署 | 成本 | 推荐场景 |
|------|------|---------|---------|------|----------|
| **bge-large-zh-v1.5** | 1024 | ⭐⭐⭐⭐⭐ | ✅ | 免费 | **中文 RAG 系统** |
| bge-m3 | 1024 | ⭐⭐⭐⭐ | ✅ | 免费 | 多语言场景 |
| bge-small-zh | 512 | ⭐⭐⭐ | ✅ | 免费 | 资源受限场景 |
| m3e-large | 1024 | ⭐⭐⭐⭐ | ✅ | 免费 | 通用中文场景 |
| text-embedding-ada-002 | 1536 | ⭐⭐⭐ | ❌ | 收费 | OpenAI 生态 |
| text-embedding-3-small | 1536 | ⭐⭐⭐⭐ | ❌ | 收费 | OpenAI 生态 |
| text-embedding-3-large | 3072 | ⭐⭐⭐⭐⭐ | ❌ | 收费 | 高精度需求 |

### 为什么选择 bge-large-zh-v1.5

1. **中文优化**：MallChat 是中文客服系统，bge-large-zh-v1.5 专为中文设计
2. **成本优势**：本地部署无 API 调用费用，适合生产环境
3. **效果优秀**：在 MTEB 中文检索任务中排名前列
4. **效率平衡**：1024 维度在检索精度和存储/计算成本间取得平衡
5. **部署灵活**：支持多种本地部署方案

---

## ⚙️ 配置说明

### 配置文件

配置文件位置：`mallchat-chat-server/src/main/resources/application-ai.yml`

```yaml
langchain4j:
  openai:
    # API Key（本地部署时可为任意值）
    api-key: ${OPENAI_API_KEY:ollama}
    # API Base URL（本地部署地址）
    base-url: ${OPENAI_BASE_URL:http://localhost:11434/v1}
    
    # Embedding Model 配置
    embedding-model:
      # 模型名称
      model-name: bge-large-zh-v1.5
      # 向量维度
      dimensions: 1024
```

### Milvus 向量库配置

```yaml
milvus:
  collection:
    name: mallchat_knowledge_vectors
    # 向量维度必须与 Embedding 模型一致
    dimension: 1024
    # 相似度度量类型（推荐 COSINE）
    metric-type: COSINE
```

### 环境变量配置

| 环境变量 | 说明 | 默认值 |
|----------|------|--------|
| OPENAI_API_KEY | API 密钥 | ollama |
| OPENAI_BASE_URL | API 基础 URL | http://localhost:11434/v1 |

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
curl http://localhost:11434/v1/embeddings \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ollama" \
  -d '{
    "model": "bge-large-zh-v1.5",
    "input": "这是一段测试文本"
  }'
```

#### 5. 应用配置

```yaml
langchain4j:
  openai:
    api-key: ollama
    base-url: http://localhost:11434/v1
    embedding-model:
      model-name: bge-large-zh-v1.5
      dimensions: 1024
```

### 方案二：vLLM 部署

#### 1. 安装 vLLM

```bash
pip install vllm
```

#### 2. 下载模型

```bash
# 从 HuggingFace 下载模型
huggingface-cli download BAAI/bge-large-zh-v1.5 --local-dir ./bge-large-zh-v1.5
```

#### 3. 启动服务

```bash
vllm serve BAAI/bge-large-zh-v1.5 \
  --port 8000 \
  --trust-remote-code
```

#### 4. 测试 Embedding

```bash
curl http://localhost:8000/v1/embeddings \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer EMPTY" \
  -d '{
    "model": "BAAI/bge-large-zh-v1.5",
    "input": "这是一段测试文本"
  }'
```

#### 5. 应用配置

```yaml
langchain4j:
  openai:
    api-key: EMPTY
    base-url: http://localhost:8000/v1
    embedding-model:
      model-name: BAAI/bge-large-zh-v1.5
      dimensions: 1024
```

### 方案三：Docker 部署

#### Ollama Docker

```bash
# 拉取镜像
docker pull ollama/ollama

# 启动容器
docker run -d \
  --name ollama \
  -p 11434:11434 \
  -v ollama_data:/root/.ollama \
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
     * @return 向量数组（1024 维）
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

### 服务实现

```java
@Slf4j
@Service
public class OpenAIEmbeddingService implements EmbeddingService {
    
    @Value("${langchain4j.openai.embedding-model.model-name:bge-large-zh-v1.5}")
    private String modelName;
    
    @Value("${langchain4j.openai.embedding-model.dimensions:1024}")
    private Integer dimensions;
    
    private EmbeddingModel embeddingModel;
    
    @PostConstruct
    public void init() {
        log.info("Initializing Embedding Model: {}, dimensions: {}", modelName, dimensions);
        
        this.embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
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

### 存储估算

| 文档数量 | 分块数量（平均 10/文档） | 向量存储空间 |
|----------|-------------------------|--------------|
| 1,000 | 10,000 | ~40MB |
| 10,000 | 100,000 | ~400MB |
| 100,000 | 1,000,000 | ~4GB |
| 1,000,000 | 10,000,000 | ~40GB |

---

## 🔧 常见问题

### Q1: 如何验证模型是否正确加载？

```java
@Test
public void testEmbeddingDimension() {
    float[] embedding = embeddingService.generateEmbedding("测试文本");
    assertEquals(1024, embedding.length);
}
```

### Q2: 向量维度不匹配怎么办？

检查以下配置是否一致：
1. `langchain4j.openai.embedding-model.dimensions`
2. `milvus.collection.dimension`
3. 实际模型输出维度

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

1. 修改配置文件中的 `model-name`
2. 同步修改 `dimensions`（如果维度不同）
3. 修改 Milvus 的 `dimension` 配置
4. 重新创建 Milvus Collection

```yaml
# 切换到 bge-m3（多语言模型）
embedding-model:
  model-name: bge-m3
  dimensions: 1024
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

---

## 📚 参考资料

### 官方资源

- [BGE 模型 GitHub](https://github.com/FlagOpen/FlagEmbedding)
- [Ollama 官方文档](https://ollama.com/docs)
- [vLLM 官方文档](https://vllm.readthedocs.io/)
- [LangChain4j 文档](https://docs.langchain4j.dev/)

### 技术论文

- [C-Pack: Packaged Resources to Advance General Chinese Embedding](https://arxiv.org/abs/2309.07597)
- [MTEB: Massive Text Embedding Benchmark](https://arxiv.org/abs/2210.07316)

### 相关文档

- [架构设计详解](./架构设计详解.md)
- [部署运维指南](./部署运维指南.md)
- [API 接口文档](./API接口文档.md)

---

## 📝 更新日志

| 日期 | 版本 | 更新内容 | 更新人 |
|------|------|---------|--------|
| 2026-05-28 | v1.0 | 创建 Embedding 模型配置指南 | Kiro |

---

**维护者**：RAG 开发团队
