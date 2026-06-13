# MallChat AI Mock 模式使用指南

> **版本**: v1.0  
> **日期**: 2026-06-13  
> **适用范围**: mallchat-ai 模块本地开发、测试、面试准备

---

## 1. 什么是 Mock 模式

Mock 模式是 MallChat AI 模块提供的一种**无外部依赖启动方案**。在 Mock 模式下：

- 不需要部署 **Ollama**
- 不需要部署 **Qdrant**
- 不需要 **OpenAI API Key**
- 不需要 **Milvus**

系统会自动使用内存/模拟实现替代真实的 LLM、Embedding 和向量存储服务，让项目能够快速启动和运行。

---

## 2. 为什么需要 Mock 模式

| 场景 | 说明 |
|------|------|
| 本地开发 | 无需搭建 GPU 环境，快速验证业务逻辑 |
| 接口调试 | 前端/客户端同学可独立调试 AI 接口 |
| 面试准备 | 演示项目时无需真实模型资源 |
| 单元测试 | 测试不依赖外部服务，稳定可重复 |
| 持续集成 | CI/CD 环境中无需部署 Ollama/Qdrant |

---

## 3. Mock 组件说明

| 组件 | 真实实现 | Mock 实现 | Mock 行为 |
|------|---------|----------|----------|
| LLM | `QwenLLMService` / `LlamaLLMService` / `OpenAILLMService` | `MockLLMService` | 返回固定模拟回复，支持流式打字机效果 |
| Embedding | `OllamaBgeEmbeddingService` / `M3eEmbeddingService` | `MockEmbeddingService` | 基于 MD5 生成确定性伪随机向量，默认 1024 维 |
| Vector Store | `QdrantVectorService` / `MilvusVectorService` | `MockVectorService` | 内存 ConcurrentHashMap 存储，重启丢失 |
| FineTune | Python FastAPI 服务 | `MockFineTuneClient` | 模拟训练全生命周期 |

---

## 4. 启动方式

### 4.1 方式一：修改主配置文件

编辑 `mallchat-chat-server/src/main/resources/application.yml`：

```yaml
spring:
  profiles:
    active: mock
```

### 4.2 方式二：启动参数

```bash
# Maven 启动
mvn spring-boot:run -pl mallchat-chat-server \
  -Dspring-boot.run.profiles=mock

# 或直接运行 jar
java -jar mallchat-chat-server/target/mallchat-chat-server.jar \
  --spring.profiles.active=mock
```

### 4.3 方式三：IDE 配置

在 IDEA 的 `Run/Debug Configurations` 中：

```
VM options: -Dspring.profiles.active=mock
```

### 4.4 方式四：使用 application-mock.yml

项目已提供 `mallchat-chat-server/src/main/resources/application-mock.yml`，内容如下：

```yaml
# Mock 模式配置
spring:
  main:
    allow-bean-definition-overriding: true
  profiles:
    active: mock

langchain4j:
  llm:
    provider: mock

embedding:
  provider: mock

vector:
  store:
    provider: mock

finetune:
  service-url: http://localhost:8000
  provider: llamafactory

logging:
  level:
    com.abin.mallchat.ai: DEBUG
    com.abin.mallchat.common.chatai: DEBUG
```

---

## 5. 配置说明

### 5.1 关键配置项

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `spring.profiles.active` | `mock` | 激活 Mock profile |
| `spring.main.allow-bean-definition-overriding` | `true` | 允许 Mock Bean 覆盖真实实现 |
| `langchain4j.llm.provider` | `mock` | 使用 Mock LLM |
| `embedding.provider` | `mock` | 使用 Mock Embedding |
| `vector.store.provider` | `mock` | 使用 Mock 向量存储 |

### 5.2 注意事项

- **必须**设置 `spring.main.allow-bean-definition-overriding: true`，否则 Mock Bean 无法覆盖真实实现，启动会报错。
- Mock 模式下真实服务类（如 `QwenLLMService`、`QdrantVectorService`）因 `@Profile("!mock")` 注解不会加载。
- Mock 向量存储是内存实现，**重启后数据会丢失**。

---

## 6. Mock 行为详解

### 6.1 MockLLMService

```java
private static final String MOCK_REPLY = 
    "【Mock模式】这是一个模拟回复。当前没有部署真实的LLM服务（如Qwen2.5-14B或Llama3-70B），请通过Ollama部署后切换配置。\n\n您的提问是：%s";
```

- 流式输出：将回复按字符拆分，每隔 10ms 发送一个 token
- 非流式输出：直接返回完整模拟回复
- 多轮对话：取最后一条用户消息作为问题

### 6.2 MockEmbeddingService

- 基于 **MD5 哈希**生成确定性伪随机向量
- 相同文本产生**相同向量**
- 默认维度：**1024**
- 向量经过 L2 归一化

示例输出：
```
输入: "MallChat 是什么？"
输出: float[1024]（确定性伪随机）
```

### 6.3 MockVectorService

- 使用 `ConcurrentHashMap` 存储向量
- 支持 `storeVectors`、`search`、`deleteVectors`、`exists`
- 相似度计算使用**余弦相似度**
- 重启后数据清空

---

## 7. 快速验证

### 7.1 启动项目

```bash
cd D:/java项目/MallChat-main
mvn spring-boot:run -pl mallchat-chat-server -Dspring-boot.run.profiles=mock
```

### 7.2 测试 AI 助手接口

```bash
# 流式问答
curl -X POST "http://localhost:8080/api/ai/assistant/question" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "userId": 10001,
    "question": "MallChat 是什么？"
  }'
```

### 7.3 测试 RAG 流式接口

```bash
curl -X POST "http://localhost:8080/api/stream/rag/query" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "userId": 10001,
    "question": "MallChat 是什么？"
  }'
```

预期响应：
```
event: message
data: {"index":0,"content":"【","finished":false,"timestamp":...}

event: message
data: {"index":1,"content":"Mock","finished":false,"timestamp":...}
...
event: done
data: {"index":N,"content":"","finished":true,"timestamp":...}
```

### 7.4 测试文档上传接口

```bash
# 创建一个测试文件
echo "MallChat 是一个基于 Spring Boot + Netty 的即时通讯项目。" > test.txt

# 上传文档
curl -X POST "http://localhost:8080/api/documents/upload" \
  -F "file=@test.txt" \
  -F "title=测试文档" \
  -F "userId=10001"
```

---

## 8. 从 Mock 模式切换到真实模式

### 8.1 本地真实模式

1. 部署 Ollama 和 Qdrant（见 [部署运维指南](../mallchat-ai-rag/docs/部署运维指南.md)）
2. 修改 `application.yml`：

```yaml
spring:
  profiles:
    active: local
```

3. 确保 `application-local.yml` 配置正确：

```yaml
langchain4j:
  llm:
    provider: qwen

embedding:
  provider: bge

vector:
  store:
    provider: qdrant
```

### 8.2 常用切换命令

```bash
# Mock 模式启动
java -jar mallchat-chat-server.jar --spring.profiles.active=mock

# 本地真实模式启动
java -jar mallchat-chat-server.jar --spring.profiles.active=local

# 仅切换 LLM 为 Mock（其他保持真实）
java -jar mallchat-chat-server.jar --langchain4j.llm.provider=mock
```

---

## 9. 常见问题

### Q1: 启动时报错 "BeanDefinitionOverrideException"

**A**: 缺少 `spring.main.allow-bean-definition-overriding: true` 配置。在 `application-mock.yml` 中已经设置，如果自定义 Mock 配置请确保包含该配置。

### Q2: Mock 模式下向量检索为空

**A**: Mock 向量存储基于内存，如果之前没有上传并索引文档，检索结果自然为空。请先调用文档上传接口，等待索引完成后再查询。

### Q3: Mock 模式下为什么 RAG 回答看起来不像真实 AI？

**A**: Mock LLM 返回的是固定模板回复，用于验证接口和流程，不代表真实模型能力。要体验真实效果请切换到 Ollama 真实模式。

### Q4: 可以在生产环境使用 Mock 模式吗？

**A**: **不可以**。Mock 模式仅用于开发和测试，生产环境必须使用真实模型服务。

### Q5: 如何单独 Mock 某个组件？

**A**: 可以通过启动参数单独指定：

```bash
# 只 Mock LLM，其他使用真实服务
java -jar mallchat-chat-server.jar \
  --langchain4j.llm.provider=mock \
  --embedding.provider=bge \
  --vector.store.provider=qdrant
```

---

## 10. 相关文档

- [AI技术方案](../mallchat-ai/docs/AI技术方案.md)
- [RAG架构设计详解](../mallchat-ai/mallchat-ai-rag/docs/架构设计详解.md)
- [RAG API接口文档](../mallchat-ai/mallchat-ai-rag/docs/API接口文档.md)
- [部署运维指南](../mallchat-ai/mallchat-ai-rag/docs/部署运维指南.md)

---

*本文档由 AI Assistant 生成，版本 v1.0，最后更新 2026-06-13。*
