# MallChat AI 技术方案

> **版本**: v2.0  
> **日期**: 2026-05-31  
> **作者**: AI Assistant  
> **适用范围**: mallchat-ai 模块

---

## 目录

1. [方案概述](#1-方案概述)
2. [技术选型对比](#2-技术选型对比)
3. [架构设计](#3-架构设计)
4. [Embedding 层](#4-embedding-层)
5. [向量数据库](#5-向量数据库)
6. [大语言模型](#6-大语言模型)
7. [微调框架](#7-微调框架)
8. [部署指南](#8-部署指南)
9. [配置参考](#9-配置参考)
10. [切换指南](#10-切换指南)
11. [常见问题](#11-常见问题)

---

## 1. 方案概述

### 1.1 为什么迁移

**旧方案的问题**:

| 问题 | 说明 |
|------|------|
| 依赖第三方 API | OpenAI、智谱等 API 存在网络延迟、稳定性风险 |
| 数据隐私 | 用户数据需传输到外部服务商 |
| 成本不可控 | API 调用按量计费，高并发场景成本高 |
| 模型选择受限 | 只能使用服务商提供的模型，无法自定义 |
| 微调困难 | 闭源模型无法进行领域微调 |

**新方案的优势**:

| 优势 | 说明 |
|------|------|
| 完全本地部署 | 所有模型运行在自有服务器，数据不出域 |
| 零 API 费用 | 一次性硬件投入，无按量计费 |
| 模型自主可控 | 可自由选择、微调、替换模型 |
| 低延迟 | 本地推理，无网络传输延迟 |
| 支持微调 | 开源模型可通过 LoRA 等方式进行领域微调 |

### 1.2 方案总览

```
┌─────────────────────────────────────────────────────────────┐
│                        MallChat AI 层                        │
├─────────────┬─────────────┬─────────────┬───────────────────┤
│  Embedding  │  向量数据库  │    LLM      │    微调框架        │
├─────────────┼─────────────┼─────────────┼───────────────────┤
│ bge-large   │   Qdrant    │ Qwen2.5-14B │ LLaMA-Factory     │
│  -zh-v1.5   │  (动态向量)  │  (Ollama)   │  (Python服务)      │
│   [推荐]    │   [推荐]    │   [推荐]    │    [推荐]         │
├─────────────┼─────────────┼─────────────┼───────────────────┤
│  m3e-base   │   Milvus    │ Llama3-70B  │    Axolotl        │
│   [备选]    │   [备选]    │   [备选]    │    [备选]         │
└─────────────┴─────────────┴─────────────┴───────────────────┘
         ↑              ↑             ↑              ↑
      Ollama        gRPC/HTTP      Ollama       HTTP REST
```

### 1.3 核心设计原则

1. **推荐/备选双轨制**: 每个组件都有推荐方案和备选方案，通过配置切换
2. **动态适配**: Qdrant 支持动态向量维度，自动适配不同 Embedding 模型
3. **统一抽象**: 所有组件通过接口抽象，底层实现可插拔
4. **条件注入**: Spring Boot `@ConditionalOnProperty` 控制组件启用

---

## 2. 技术选型对比

### 2.1 完整对比表

| 层级 | 推荐方案 | 备选方案 | 旧方案 | 选型理由 |
|------|---------|---------|--------|---------|
| **Embedding** | bge-large-zh-v1.5 | m3e-base | OpenAI API | 中文语义理解更强，本地部署 |
| **向量维度** | 1024 | 768 | 1536 | bge 针对中文优化 |
| **向量数据库** | Qdrant | Milvus | Milvus | 动态向量支持，部署简单 |
| **大模型** | Qwen2.5-14B | Llama3-70B | GPT-3.5/4 | 中文能力更强，显存需求更低 |
| **推理框架** | Ollama | Ollama | HTTP API | 一键部署，管理方便 |
| **微调框架** | LLaMA-Factory | Axolotl | 无 | 配置简单，社区活跃 |
| **Java 集成** | LangChain4j 0.36 | LangChain4j 0.36 | LangChain4j 0.27 | 支持 Ollama、Qdrant |

### 2.2 Qwen2.5-14B vs Llama3-70B

| 维度 | Qwen2.5-14B | Llama3-70B |
|------|-------------|------------|
| **显存需求** | ~28GB (FP16) | ~40GB+ (FP16) |
| **中文能力** | ⭐⭐⭐⭐⭐ 原生优化 | ⭐⭐⭐☆☆ 需微调 |
| **推理速度** | 更快（参数量小） | 较慢（参数量大） |
| **代码能力** | ⭐⭐⭐⭐☆ | ⭐⭐⭐⭐⭐ |
| **英文能力** | ⭐⭐⭐⭐☆ | ⭐⭐⭐⭐⭐ |
| **推荐场景** | 中文对话、客服 | 代码生成、英文场景 |

### 2.3 bge-large-zh-v1.5 vs m3e-base

| 维度 | bge-large-zh-v1.5 | m3e-base |
|------|-------------------|----------|
| **输出维度** | 1024 | 768 |
| **模型大小** | ~1.3GB | ~400MB |
| **中文语义** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐☆ |
| **推理速度** | 较慢 | 更快 |
| **显存需求** | ~3GB | ~1GB |
| **推荐场景** | 高精度检索 | 资源受限环境 |

### 2.4 Qdrant vs Milvus

| 维度 | Qdrant | Milvus |
|------|--------|--------|
| **部署复杂度** | 简单（单容器） | 复杂（多组件） |
| **动态向量** | ✅ 原生支持 | ❌ 需固定维度 |
| **内存占用** | 低（支持磁盘存储） | 高（需加载到内存） |
| **查询性能** | 高 | 极高（大规模时） |
| **生态工具** | 较少 | 丰富 |
| **推荐场景** | 中小型项目、快速迭代 | 超大规模、复杂查询 |

---

## 3. 架构设计

### 3.1 模块关系图

```
┌──────────────────────────────────────────────────────────────────┐
│                         mallchat-ai                               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐   │
│  │ mallchat-ai  │  │ mallchat-ai  │  │   mallchat-ai-llm    │   │
│  │   -vector    │  │    -rag      │  │                      │   │
│  │              │  │              │  │  ┌────────────────┐  │   │
│  │ ┌──────────┐ │  │ ┌──────────┐ │  │  │ OpenAILLM      │  │   │
│  │ │ Vector   │ │  │ │ RAG      │ │  │  │   Service      │  │   │
│  │ │ Service  │◄┼──┼─┤ Service  │ │  │  └────────────────┘  │   │
│  │ │ (接口)   │ │  │ │          │ │  │  ┌────────────────┐  │   │
│  │ └────┬─────┘ │  │ └──────────┘ │  │  │ ChatGLMLLM     │  │   │
│  │      │       │  └──────────────┘  │  │   Service      │  │   │
│  │  ┌───┴────┐  │        ▲           │  └────────────────┘  │   │
│  │  ▼        ▼  │        │           │  ┌────────────────┐  │   │
│  │ ┌────┐  ┌────┐        │           │  │ QwenLLM        │  │   │
│  │ │Qdrant│  │Milvus│      │           │  │   Service  ◄──┼──┼───┤
│  │ │Svc │  │Svc │      │           │  │  [推荐]        │  │   │
│  │ └────┘  └────┘      │           │  └────────────────┘  │   │
│  │      ▲              │           │  ┌────────────────┐  │   │
│  │      │              │           │  │ LlamaLLM       │  │   │
│  │ ┌────┴────┐         │           │  │   Service  ◄──┼──┼───┤
│  │ ▼         ▼         │           │  │  [备选]        │  │   │
│  │ ┌────┐  ┌────┐      │           │  └────────────────┘  │   │
│  │ │ BGE  │  │M3E │      │           │                      │   │
│  │ │Svc │  │Svc │      │           │  ┌────────────────┐  │   │
│  │ └────┘  └────┘      │           │  │ LLMService     │  │   │
│  │ [推荐]  [备选]       │           │  │   Factory      │  │   │
│  └─────────────────────┘           │  └────────────────┘  │   │
│                                    └──────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │              mallchat-ai-finetune                        │  │
│  │  ┌────────────────┐         ┌────────────────────────┐  │  │
│  │  │ Java 客户端     │◄───────►│ Python FastAPI 服务     │  │  │
│  │  │ FineTuneClient │  HTTP   │  ┌──────────────────┐  │  │  │
│  │  │ FineTuneService│         │  │ LLaMA-Factory    │  │  │  │
│  │  └────────────────┘         │  │   Service [推荐] │  │  │  │
│  │                              │  ├──────────────────┤  │  │  │
│  │                              │  │ Axolotl          │  │  │  │
│  │                              │  │   Service [备选] │  │  │  │
│  │                              │  └──────────────────┘  │  │  │
│  │                              └────────────────────────┘  │  │
│  └─────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────┘
```

### 3.2 数据流图

```
用户提问
    │
    ▼
┌─────────────┐
│ AI Assistant │
│  Controller  │
└──────┬──────┘
       │
       ▼
┌─────────────┐     ┌─────────────┐
│   RAG       │◄────┤  Embedding  │
│  Service    │     │   Service   │
│             │     │ (bge/m3e)   │
└──────┬──────┘     └─────────────┘
       │
       ▼
┌─────────────┐     ┌─────────────┐
│   Vector    │◄────┤   Qdrant    │
│   Search    │     │   /Milvus   │
└──────┬──────┘     └─────────────┘
       │
       ▼
┌─────────────┐
│    LLM      │
│  Service    │
│(Qwen/Llama) │
└──────┬──────┘
       │
       ▼
   流式响应
```

---

## 4. Embedding 层

### 4.1 职责

将文本转换为高维向量，用于语义检索。是 RAG（检索增强生成）的核心组件。

### 4.2 实现列表

| 实现类 | 模型 | 维度 | 条件 |
|--------|------|------|------|
| `OllamaBgeEmbeddingService` | bge-large-zh-v1.5 | 1024 | `embedding.provider=bge` (默认) |
| `M3eEmbeddingService` | m3e-base | 768 | `embedding.provider=m3e` |
| `OpenAIEmbeddingService` | 任意 OpenAI 兼容模型 | 可配置 | `embedding.provider=openai` |

### 4.3 为什么选 bge-large-zh-v1.5

1. **中文优化**: 专门针对中文语义理解训练，在中文评测集上表现优异
2. **长文本**: 支持最长 512 tokens 的输入
3. **开源可商用**: MIT 协议，无商用限制
4. **社区活跃**: 北京智源人工智能研究院维护，持续更新

### 4.4 Ollama 部署

```bash
# 拉取模型
ollama pull bge-large-zh-v1.5

# 验证
ollama run bge-large-zh-v1.5

# 查看已安装模型
ollama list
```

### 4.5 代码示例

```java
@Autowired
private EmbeddingService embeddingService;

// 生成单个向量
float[] vector = embeddingService.generateEmbedding(" MallChat 是什么？");
// vector.length == 1024 (bge) 或 768 (m3e)

// 批量生成
List<float[]> vectors = embeddingService.generateEmbeddings(
    Arrays.asList("问题1", "问题2", "问题3")
);
```

---

## 5. 向量数据库

### 5.1 职责

存储和检索高维向量，实现语义相似度搜索。是 RAG 的"记忆"组件。

### 5.2 实现列表

| 实现类 | 数据库 | 条件 |
|--------|--------|------|
| `QdrantVectorService` | Qdrant | `vector.store.provider=qdrant` (默认) |
| `MilvusVectorService` | Milvus | `vector.store.provider=milvus` |

### 5.3 动态向量（核心特性）

**问题**: bge-large-zh-v1.5 输出 1024 维向量，m3e-base 输出 768 维向量。传统向量数据库要求所有向量维度一致。

**Qdrant 解决方案**:

```protobuf
// Qdrant Collection 配置
vector_params {
    distance: Cosine
    on_disk: true        // 向量落盘，降低内存
    dynamic: true        // 关键：支持不同维度
}
```

开启 `dynamic: true` 后：
- 同一 Collection 可存储 1024 维和 768 维向量
- 切换 Embedding 模型无需重建 Collection
- 维度由插入的第一个向量自动确定

### 5.4 Qdrant 部署

```bash
# Docker 部署
docker run -d \
    --name qdrant \
    -p 6333:6333 \
    -p 6334:6334 \
    -v $(pwd)/qdrant_storage:/qdrant/storage \
    qdrant/qdrant:latest

# 验证
http://localhost:6333/dashboard
```

### 5.5 代码示例

```java
@Autowired
private VectorService vectorService;

// 存储向量
vectorService.storeVectors(documentId, chunks);

// 语义检索
List<SearchResult> results = vectorService.search(
    queryVector,  // float[]
    topK,         // 返回数量，如 5
    documentId    // 限定文档（null 为全局检索）
);

// 删除向量（幂等）
vectorService.deleteVectors(documentId);
```

---

## 6. 大语言模型

### 6.1 职责

理解用户意图，生成自然语言回复。是 AI 助手的"大脑"。

### 6.2 实现列表

| 实现类 | 模型 | 部署方式 | 条件 |
|--------|------|---------|------|
| `QwenLLMService` | Qwen2.5-14B | Ollama | `langchain4j.llm.provider=qwen-ollama` (推荐) |
| `LlamaLLMService` | Llama3-70B | Ollama | `langchain4j.llm.provider=llama` (备选) |
| `OpenAILLMService` | GPT-3.5/4 | OpenAI API | `langchain4j.llm.provider=openai` |
| `ChatGLMLLMService` | ChatGLM | 智谱 API | `langchain4j.llm.provider=chatglm` |

### 6.3 为什么选 Qwen2.5-14B

1. **中文最强开源模型**: 在 C-Eval、CMMLU 等中文评测集上领先
2. **代码能力**: HumanEval 得分高，支持多种编程语言
3. **工具调用**: 原生支持 Function Calling，便于扩展
4. **显存友好**: 14B 参数，FP16 仅需 ~28GB 显存
5. **长上下文**: 支持 128K 上下文窗口

### 6.4 Ollama 部署

```bash
# 拉取 Qwen2.5-14B
ollama pull qwen2.5:14b

# 拉取 Llama3-70B（需要更大显存）
ollama pull llama3:70b

# 查看已安装模型
ollama list

# 运行模型（测试）
ollama run qwen2.5:14b

# 查看模型信息
ollama show qwen2.5:14b
```

### 6.5 显存需求参考

| 模型 | 量化方式 | 显存需求 | 推荐 GPU |
|------|---------|---------|---------|
| Qwen2.5-14B | FP16 | ~28GB | RTX 4090 (24GB) ×2 或 A100 40GB |
| Qwen2.5-14B | Q4_K_M | ~10GB | RTX 4090 24GB |
| Llama3-70B | FP16 | ~40GB+ | A100 80GB 或 2×A100 40GB |
| Llama3-70B | Q4_K_M | ~40GB | A100 80GB |

### 6.6 代码示例

```java
@Autowired
private LLMServiceFactory llmServiceFactory;

// 获取默认 LLM 服务
LLMService llmService = llmServiceFactory.getDefaultService();

// 流式对话
Flux<String> response = llmService.streamChat(
    "MallChat 是什么？",
    LLMOptions.defaultOptions()
);

// 非流式对话
String response = llmService.chat(
    "MallChat 是什么？",
    LLMOptions.defaultOptions()
);

// 多轮对话
List<ChatMessage> messages = Arrays.asList(
    UserMessage.from("你好"),
    AiMessage.from("你好！有什么可以帮您的？"),
    UserMessage.from("MallChat 是什么？")
);
String response = llmService.chat(messages, LLMOptions.defaultOptions());
```

---

## 7. 微调框架

### 7.1 职责

在预训练大模型基础上，使用领域数据进行微调，使模型更适配特定业务场景。

### 7.2 架构设计

```
┌──────────────────────────────────────────────────────┐
│                  Java 项目 (mallchat-ai)              │
│  ┌──────────────────────────────────────────────┐   │
│  │     mallchat-ai-finetune (Java 客户端)        │   │
│  │  ┌────────────┐  ┌────────────┐  ┌─────────┐ │   │
│  │  │ FineTune   │  │ FineTune   │  │ DTOs    │ │   │
│  │  │ Client     │  │ Service    │  │         │ │   │
│  │  └─────┬──────┘  └────────────┘  └─────────┘ │   │
│  └────────┼──────────────────────────────────────┘   │
└───────────┼──────────────────────────────────────────┘
            │ HTTP REST API
            ▼
┌──────────────────────────────────────────────────────┐
│              Python 服务 (FastAPI)                    │
│  ┌──────────────────────────────────────────────┐   │
│  │              app/main.py                      │   │
│  │  ┌────────────┐  ┌──────────────────────┐   │   │
│  │  │ Task       │  │ Provider Router      │   │   │
│  │  │ Manager    │  │ (llamafactory/axolotl)│   │   │
│  │  └─────┬──────┘  └──────────┬───────────┘   │   │
│  └────────┼────────────────────┼───────────────┘   │
│           ▼                    ▼                   │
│  ┌──────────────────┐  ┌──────────────────┐       │
│  │ LLaMA-Factory    │  │ Axolotl          │       │
│  │ Service [推荐]   │  │ Service [备选]   │       │
│  │                  │  │                  │       │
│  │ • 配置驱动       │  │ • YAML 配置      │       │
│  │ • LoRA 微调      │  │ • LoRA 微调      │       │
│  │ • DeepSpeed      │  │ • DeepSpeed      │       │
│  │ • 模型合并       │  │ • 模型合并       │       │
│  └──────────────────┘  └──────────────────┘       │
└──────────────────────────────────────────────────────┘
```

### 7.3 为什么选 LLaMA-Factory

1. **配置简单**: YAML 配置文件驱动，无需写代码
2. **支持广泛**: 支持 100+ 模型，包括 Qwen、Llama、ChatGLM 等
3. **训练方式多样**: 支持预训练、SFT、RLHF、DPO 等
4. **显存优化**: 支持 DeepSpeed、FSDP、QLoRA 等
5. **社区活跃**: GitHub 60K+ Stars，文档完善

### 7.4 微调流程

```
准备数据 ──► 选择模型 ──► 配置参数 ──► 启动训练 ──► 评估模型 ──► 合并导出 ──► 部署上线
    │           │           │           │           │           │
    ▼           ▼           ▼           ▼           ▼           ▼
JSONL      Qwen2.5-14B   LoRA r=64    监控日志    验证集测试   合并 LoRA   Ollama
格式       Llama3-70B    epoch=3      查看损失    计算 BLEU    权重       导入
```

### 7.5 部署微调服务

```bash
cd mallchat-ai-finetune/python-service

# Docker Compose 启动
docker-compose up -d

# 查看日志
docker-compose logs -f mallchat-finetune

# 健康检查
curl http://localhost:8000/health
```

### 7.6 Java 调用示例

```java
@Autowired
private FineTuneService fineTuneService;

// 提交微调任务（LLaMA-Factory）
Mono<FineTuneResponse> response = fineTuneService.fineTuneWithLlamaFactory(
    "qwen2.5:14b",                    // 基础模型
    "/data/training_data.jsonl"       // 训练数据
);

// 查询任务状态
Mono<FineTuneStatusResponse> status = fineTuneService.getTaskStatus(taskId);

// 自定义训练配置
FineTuneRequest request = FineTuneRequest.builder()
    .provider("llamafactory")
    .baseModel("qwen2.5:14b")
    .datasetPath("/data/training.jsonl")
    .loraConfig(FineTuneRequest.LoraConfig.builder()
        .r(64)
        .loraAlpha(128)
        .build())
    .trainingConfig(FineTuneRequest.TrainingConfig.builder()
        .numTrainEpochs(3)
        .learningRate(5e-5)
        .build())
    .build();

Mono<FineTuneResponse> response = fineTuneService.submitFineTune(request);
```

---

## 8. 部署指南

### 8.1 完整部署流程

#### 步骤 1: 环境准备

```bash
# 系统要求
# - OS: Ubuntu 22.04 / CentOS 8 / Windows 11 WSL2
# - GPU: NVIDIA RTX 4090 24GB 或 A100 40GB
# - CPU: 16核+
# - 内存: 64GB+
# - 磁盘: 500GB+ SSD

# 安装 NVIDIA 驱动
ubuntu-drivers devices
sudo ubuntu-drivers autoinstall

# 安装 CUDA
wget https://developer.download.nvidia.com/compute/cuda/12.1.0/local_installers/cuda_12.1.0_530.30.02_linux.run
sudo sh cuda_12.1.0_530.30.02_linux.run

# 安装 Docker
sudo apt-get update
sudo apt-get install -y docker.io docker-compose
sudo usermod -aG docker $USER
```

#### 步骤 2: 安装 Ollama

```bash
# 一键安装
curl -fsSL https://ollama.com/install.sh | sh

# 配置环境变量
export OLLAMA_HOST=0.0.0.0:11434
export OLLAMA_ORIGINS=*

# 启动服务
sudo systemctl enable ollama
sudo systemctl start ollama

# 拉取模型
ollama pull qwen2.5:14b
ollama pull bge-large-zh-v1.5

# 验证
ollama list
curl http://localhost:11434/api/tags
```

#### 步骤 3: 部署 Qdrant

```bash
# Docker 部署
docker run -d \
    --name qdrant \
    -p 6333:6333 \
    -p 6334:6334 \
    -v $(pwd)/qdrant_storage:/qdrant/storage \
    qdrant/qdrant:latest

# 验证
curl http://localhost:6333
```

#### 步骤 4: 部署微调服务（可选）

```bash
cd mallchat-ai/mallchat-ai-finetune/python-service

# 构建镜像
docker-compose build

# 启动服务
docker-compose up -d

# 验证
curl http://localhost:8000/health
```

#### 步骤 5: 启动 Java 项目

```bash
# 配置 application.yml（见第 9 节）

# 编译
mvn clean compile -pl mallchat-ai -am

# 启动
mvn spring-boot:run -pl mallchat-ai/mallchat-ai-assistant
```

### 8.2 Docker Compose 完整编排

```yaml
version: '3.8'

services:
  qdrant:
    image: qdrant/qdrant:latest
    ports:
      - "6333:6333"
      - "6334:6334"
    volumes:
      - qdrant_data:/qdrant/storage
    restart: unless-stopped

  ollama:
    image: ollama/ollama:latest
    ports:
      - "11434:11434"
    volumes:
      - ollama_data:/root/.ollama
    environment:
      - OLLAMA_HOST=0.0.0.0:11434
    deploy:
      resources:
        reservations:
          devices:
            - driver: nvidia
              count: all
              capabilities: [gpu]
    restart: unless-stopped

  mallchat-finetune:
    build:
      context: ./mallchat-ai/mallchat-ai-finetune/python-service
      dockerfile: Dockerfile
    ports:
      - "8000:8000"
    volumes:
      - finetune_data:/app/data
      - finetune_outputs:/app/outputs
    environment:
      - CUDA_VISIBLE_DEVICES=0
    deploy:
      resources:
        reservations:
          devices:
            - driver: nvidia
              count: all
              capabilities: [gpu]
    restart: unless-stopped

  mallchat-app:
    build:
      context: .
      dockerfile: Dockerfile
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
    depends_on:
      - qdrant
      - ollama
    restart: unless-stopped

volumes:
  qdrant_data:
  ollama_data:
  finetune_data:
  finetune_outputs:
```

---

## 9. 配置参考

### 9.1 完整配置（推荐方案）

```yaml
# application.yml - 推荐配置（全本地部署）

# ==================== Embedding 配置 ====================
embedding:
  provider: bge   # 推荐: bge | 备选: m3e | 兼容: openai

ollama:
  base-url: http://localhost:11434
  embedding-model: bge-large-zh-v1.5
  timeout: 60s
  max-retries: 3

# ==================== 向量数据库配置 ====================
vector:
  store:
    provider: qdrant   # 推荐: qdrant | 备选: milvus

qdrant:
  host: localhost
  port: 6334
  api-key:              # 如无认证则留空
  collection-name: mallchat_knowledge
  grpc-timeout: 30
  use-tls: false

# Milvus 配置（备选，启用时取消注释）
# milvus:
#   host: localhost
#   port: 19530
#   collection:
#     name: mallchat_knowledge
#     dimension: 1024
#     index-type: IVF_FLAT
#     metric-type: COSINE

# ==================== LLM 配置 ====================
langchain4j:
  llm:
    provider: qwen-ollama   # 推荐: qwen-ollama | 备选: llama
    fallback-provider: llama

ollama:
  base-url: http://localhost:11434
  model-name: qwen2.5:14b
  temperature: 0.7
  timeout: 120s

# OpenAI 配置（兼容旧方案）
# spring:
#   ai:
#     openai:
#       api-key: sk-xxx
#       base-url: https://api.openai.com
#       model: gpt-3.5-turbo

# ==================== 微调服务配置 ====================
finetune:
  service-url: http://localhost:8000
  provider: llamafactory   # 推荐: llamafactory | 备选: axolotl
  timeout: 300
  max-retries: 3
```

### 9.2 最小配置（快速开始）

```yaml
# 仅需配置这三个即可运行
embedding:
  provider: bge

vector:
  store:
    provider: qdrant

langchain4j:
  llm:
    provider: qwen-ollama
```

### 9.3 环境变量配置

```bash
# 生产环境建议通过环境变量注入敏感配置
export OLLAMA_BASE_URL=http://localhost:11434
export QDRANT_HOST=localhost
export QDRANT_PORT=6334
export FINETUNE_SERVICE_URL=http://localhost:8000

# 应用启动时自动读取
# spring-boot 会自动将环境变量映射到配置属性
```

---

## 10. 切换指南

### 10.1 Embedding 切换

```yaml
# 切换到 bge-large-zh-v1.5（推荐）
embedding:
  provider: bge
ollama:
  embedding-model: bge-large-zh-v1.5

# 切换到 m3e-base（备选，资源受限）
embedding:
  provider: m3e
ollama:
  embedding-model: m3e

# 切换到 OpenAI API（兼容旧方案）
embedding:
  provider: openai
langchain4j:
  openai:
    api-key: sk-xxx
    embedding-model:
      model-name: text-embedding-3-large
```

### 10.2 向量数据库切换

```yaml
# 切换到 Qdrant（推荐）
vector:
  store:
    provider: qdrant

# 切换到 Milvus（备选）
vector:
  store:
    provider: milvus
```

> ⚠️ **注意**: 切换向量数据库时，**需要重新导入数据**。建议：
> 1. 导出旧数据库数据
> 2. 切换配置
> 3. 导入到新数据库

### 10.3 大模型切换

```yaml
# 切换到 Qwen2.5-14B（推荐，中文场景）
langchain4j:
  llm:
    provider: qwen-ollama
ollama:
  model-name: qwen2.5:14b

# 切换到 Llama3-70B（备选，英文/代码场景）
langchain4j:
  llm:
    provider: llama
ollama:
  model-name: llama3:70b

# 降级到 OpenAI（应急）
langchain4j:
  llm:
    provider: openai
spring:
  ai:
    openai:
      api-key: sk-xxx
```

### 10.4 微调框架切换

```yaml
# 使用 LLaMA-Factory（推荐）
finetune:
  provider: llamafactory

# 使用 Axolotl（备选）
finetune:
  provider: axolotl
```

### 10.5 降级策略

```yaml
# 配置降级，当主服务不可用时自动切换
langchain4j:
  llm:
    provider: qwen-ollama
    fallback-provider: llama   # Qwen 不可用时降级到 Llama

# 如果本地模型都不可用，降级到 OpenAI（需要配置）
# fallback-provider: openai
```

---

## 11. 常见问题

### Q1: Ollama 模型下载慢怎么办？

**A**: 设置镜像加速：
```bash
# 使用国内镜像（如 modelscope）
export OLLAMA_MODELS=/path/to/models
ollama pull qwen2.5:14b

# 或手动下载后导入
# 从 ModelScope / HuggingFace 下载 GGUF 格式模型
# 编写 Modelfile，执行 ollama create
```

### Q2: 显存不足怎么办？

**A**: 使用量化版本：
```bash
# Q4_K_M 量化，显存需求降低 60%
ollama pull qwen2.5:14b-q4_K_M
ollama pull llama3:70b-q4_K_M

# 配置中使用量化版本
ollama:
  model-name: qwen2.5:14b-q4_K_M
```

### Q3: 如何验证服务是否正常？

**A**: 使用健康检查端点：
```bash
# Ollama
curl http://localhost:11434/api/tags

# Qdrant
curl http://localhost:6333

# 微调服务
curl http://localhost:8000/health

# Java 应用
curl http://localhost:8080/actuator/health
```

### Q4: 切换 Embedding 模型后向量不匹配？

**A**: 
1. Qdrant 动态向量已自动适配，无需操作
2. Milvus 需要重建 Collection：
   - 删除旧 Collection
   - 修改 `milvus.collection.dimension` 为新的维度（1024 或 768）
   - 重新导入数据

### Q5: 如何添加自定义模型？

**A**: 
1. **Ollama 方式**（推荐）：
   ```bash
   # 创建 Modelfile
   cat > Modelfile << EOF
   FROM ./your-model.gguf
   PARAMETER temperature 0.7
   SYSTEM "你是一个智能助手"
   EOF

   # 创建模型
   ollama create my-model -f Modelfile
   ```

2. **新增 Java 服务**：
   - 实现 `LLMService` 接口
   - 添加 `@ConditionalOnProperty` 条件
   - 在 `LLMServiceFactory` 中注册

### Q6: 微调后的模型如何使用？

**A**: 
1. 合并 LoRA 权重到基础模型
2. 导出为 GGUF 格式
3. 导入到 Ollama：
   ```bash
   ollama create my-finetuned-model -f Modelfile
   ```
4. 修改配置使用新模型：
   ```yaml
   ollama:
     model-name: my-finetuned-model
   ```

### Q7: LangChain4j 0.36 与 0.27 的兼容性？

**A**: 主要变化：
- `StreamingResponseHandler` 接口保持不变 ✅
- `ChatLanguageModel` 接口保持不变 ✅
- 新增 `OllamaChatModel`、Ollama 相关类 ✅
- 部分内部类包名可能调整 ⚠️

如遇编译错误，检查：
```xml
<!-- 确保使用统一版本 -->
<langchain4j.version>0.36.0</langchain4j.version>
```

---

## 附录

### A. 相关文件清单

| 文件 | 说明 |
|------|------|
| `mallchat-ai/pom.xml` | AI 模块父 POM，管理依赖版本 |
| `mallchat-ai-vector/pom.xml` | 向量模块，新增 Qdrant、Ollama 依赖 |
| `mallchat-ai-llm/pom.xml` | LLM 模块，新增 Ollama 依赖 |
| `QdrantVectorService.java` | Qdrant 向量服务实现 |
| `MilvusVectorService.java` | Milvus 向量服务实现（备选） |
| `OllamaBgeEmbeddingService.java` | BGE Embedding 服务（推荐） |
| `M3eEmbeddingService.java` | M3E Embedding 服务（备选） |
| `OpenAIEmbeddingService.java` | OpenAI Embedding 服务（兼容） |
| `QwenLLMService.java` | Qwen2.5-14B LLM 服务（推荐） |
| `LlamaLLMService.java` | Llama3-70B LLM 服务（备选） |
| `OpenAILLMService.java` | OpenAI LLM 服务（兼容） |
| `ChatGLMLLMService.java` | ChatGLM LLM 服务（兼容） |
| `LLMServiceFactory.java` | LLM 服务工厂，管理多提供商 |
| `mallchat-ai-finetune/` | 微调框架模块 |

### B. 参考链接

| 资源 | 链接 |
|------|------|
| Ollama 官方 | https://ollama.com |
| Qdrant 官方 | https://qdrant.tech |
| LangChain4j | https://docs.langchain4j.dev |
| LLaMA-Factory | https://github.com/hiyouga/LLaMA-Factory |
| Axolotl | https://github.com/OpenAccess-AI-Collective/axolotl |
| BGE Embedding | https://github.com/FlagOpen/FlagEmbedding |
| Qwen2.5 | https://github.com/QwenLM/Qwen2.5 |
| Llama3 | https://github.com/meta-llama/llama3 |

---

*本文档由 AI Assistant 生成，如有问题请及时反馈。*
