# MallChat AI模块 - 架构总览与优化路线图

> 本文档描述MallChat项目中AI模块的整体架构、核心流程、技术选型及未来优化方向。
> 文档版本: v1.0 | 更新时间: 2026-05-17

---

## 目录

1. [架构总览](#一架构总览)
2. [模块详解](#二模块详解)
3. [核心流程](#三核心流程)
4. [技术栈与选型](#四技术栈与选型)
5. [接口设计](#五接口设计)
6. [优化路线图](#六优化路线图)
7. [AI Agent应用开发知识](#七ai-agent应用开发知识)

---

## 一、架构总览

### 1.1 系统定位

MallChat AI模块是一个集成化的智能服务子系统，为即时通讯应用提供以下AI能力：

- **RAG知识问答**: 基于私有知识库的检索增强生成
- **AI智能助手**: 支持多轮对话的通用AI助手
- **文档智能处理**: 自动解析、分块、索引各类文档
- **流式交互**: 实时SSE流式输出，提升用户体验

### 1.2 整体架构图

```
+---------------------+     +---------------------+     +---------------------+
|   前端 (Vue 3)       |     |   前端 (Vue 3)       |     |   前端 (Vue 3)       |
|   AI助手聊天面板     |     |   知识库管理页       |     |   文档上传页         |
|   SSE流式接收        |     |   HTTP API          |     |   HTTP API          |
+----------+----------+     +----------+----------+     +----------+----------+
           |                           |                           |
           +-----------+---------------+---------------+-----------+
                       |                               |
           +-----------v---------------+   +-----------v-----------+
           |   AIAssistantController   |   |   DocumentController  |
           |   /api/ai/assistant/*     |   |   /api/documents/*    |
           +-----------+---------------+   +-----------+-----------+
                       |                               |
           +-----------v---------------+   +-----------v-----------+
           |   AIAssistantServiceImpl  |   |   RAGServiceImpl      |
           |   - 多轮对话管理          |   |   - 检索+生成编排     |
           |   - 上下文截断            |   |   - 缓存策略          |
           |   - 对话历史持久化        |   |   - 降级处理          |
           +-----------+---------------+   +-----------+-----------+
                       |                               |
           +-----------v---------------+   +-----------v-----------+
           |      LLMService           |   |   VectorService       |
           |   (OpenAI/ChatGLM/...)    |   |   (Milvus)            |
           |   - 流式/非流式调用       |   |   - 存储/检索/删除    |
           |   - 熔断降级              |   |   - 相似度搜索        |
           |   - Token计数             |   |   - 幂等操作          |
           +-----------+---------------+   +-----------+-----------+
                       |                               |
           +-----------v---------------+   +-----------v-----------+
           |   LangChain4j             |   |   EmbeddingService    |
           |   - ChatLanguageModel     |   |   (OpenAI Embedding)  |
           |   - StreamingChatModel    |   |   - 文本向量化        |
           |   - OpenAiTokenizer       |   |   - 批量处理          |
           +---------------------------+   +-----------------------+
```

### 1.3 模块依赖关系

```
                    mallchat-chat-server (主服务入口)
                             |
        +--------------------+--------------------+
        |                    |                    |
   mallchat-ai-assistant  mallchat-ai-rag    mallchat-ai-llm
        |                    |                    |
        +--------------------+--------------------+
                             |
                    mallchat-ai-vector
                             |
                    mallchat-ai-common
                             |
                    mallchat-common-starter
```

**依赖原则**: 单向依赖，AI模块不依赖chat-server，避免循环依赖。

---

## 二、模块详解

### 2.1 mallchat-ai-common (公共模块)

**职责**: 提供公共实体、DAO接口、枚举和异常定义

**核心组件**:

| 类/接口 | 说明 |
|---------|------|
| `AIConversation` | AI对话历史实体 |
| `KnowledgeDocument` | 知识文档实体 |
| `DocumentChunk` | 文档分块实体 |
| `ConversationType` | 对话类型枚举(QA/RAG/SUMMARY) |
| `IndexStatus` | 索引状态枚举(PENDING/INDEXING/COMPLETED/FAILED) |
| `AIException` | AI模块基础异常 |
| `AIConversationDao` | 对话历史数据访问 |
| `KnowledgeDocumentDao` | 知识文档数据访问 |
| `DocumentChunkDao` | 文档分块数据访问 |

### 2.2 mallchat-ai-llm (LLM服务模块)

**职责**: 封装大语言模型调用，提供统一接口屏蔽不同模型差异

**核心设计**:

```java
// 统一接口
public interface LLMService {
    Flux<String> streamChat(String prompt, LLMOptions options);           // 流式单轮
    String chat(String prompt, LLMOptions options);                       // 非流式单轮
    Flux<String> streamChat(List<ChatMessage> messages, LLMOptions options); // 流式多轮
    String chat(List<ChatMessage> messages, LLMOptions options);          // 非流式多轮
    int countTokens(String text);                                         // Token计数
}

// 工厂模式管理多提供商
@Component
public class LLMServiceFactory {
    private Map<LLMProvider, LLMService> serviceMap;
    public LLMService getDefaultService();
    public LLMService getService(LLMProvider provider);
}
```

**已实现**:
- `OpenAILLMService`: 基于LangChain4j的OpenAI完整实现
- `ChatGLMLLMService`: ChatGLM框架（待完善初始化）

**待实现**:
- `QwenLLMService`: 通义千问
- `DeepSeekLLMService`: DeepSeek
- `ClaudeLLMService`: Claude

** resilience4j 熔断配置**:

```yaml
resilience4j.circuitbreaker:
  configs:
    default:
      failureRateThreshold: 50
      slowCallRateThreshold: 50
      slowCallDurationThreshold: 5s
      slidingWindowSize: 10
      minimumNumberOfCalls: 5
      waitDurationInOpenState: 60s
```

### 2.3 mallchat-ai-vector (向量服务模块)

**职责**: 向量数据库操作和文本向量化

**核心组件**:

| 类 | 职责 |
|----|------|
| `VectorService` | 向量存储、检索、删除接口 |
| `MilvusVectorService` | Milvus实现（含连接池管理） |
| `EmbeddingService` | 文本向量化接口 |
| `OpenAIEmbeddingService` | OpenAI Embedding实现 |
| `MilvusConnectionPool` | Milvus连接池（支持健康检查） |

**Milvus集合设计**:

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Int64 | 主键，自增 |
| document_id | Int64 | 关联文档ID |
| chunk_id | Int64 | 关联分块ID |
| chunk_index | Int32 | 分块序号 |
| content | VarChar(65535) | 分块内容 |
| vector | FloatVector(1536) | 向量数据 |
| metadata | VarChar(65535) | 元数据JSON |

**索引配置**:
- 索引类型: IVF_FLAT
- 度量方式: COSINE
- nlist: 1024 (聚类中心数)
- nprobe: 10 (搜索时探测的聚类数)

### 2.4 mallchat-ai-rag (RAG核心模块)

**职责**: RAG流程编排、文档处理、流式输出管理

**核心流程**:

```
用户提问
    |
    v
[检查索引状态] --未就绪--> 返回友好提示
    |已就绪
    v
[查询缓存] --命中--> 使用缓存结果
    |未命中
    v
[Embedding向量化]
    |
    v
[Milvus相似度检索] --空结果--> 降级到普通QA
    |有结果
    v
[构造RAG Prompt]
    |
    v
[LLM流式生成] --> SSE逐字推送
    |
    v
[保存对话历史]
```

**文档处理流水线**:

```
上传文档
    |
    v
[格式验证] --非法--> 返回错误
    |合法
    v
[本地存储/OSS上传]
    |
    v
[保存DB - 状态PENDING]
    |
    v
[RocketMQ异步任务]
    |
    v
[Apache Tika解析] --> 提取纯文本
    |
    v
[智能分块]
    |-- 固定长度分块 (通用文档)
    |-- 语义分块 (Markdown/HTML)
    |-- 递归分块 (代码文件)
    |
    v
[批量Embedding生成]
    |
    v
[Milvus向量存储]
    |
    v
[更新DB状态 - COMPLETED]
```

**流式输出控制**:

| 特性 | 实现 |
|------|------|
| SSE协议 | `MediaType.TEXT_EVENT_STREAM_VALUE` |
| 心跳机制 | 30秒间隔keep-alive |
| 超时检测 | 300秒连接超时 |
| 连接管理 | `StreamConnectionManager`跟踪活跃连接 |
| 事件类型 | message / done / error / heartbeat |

### 2.5 mallchat-ai-assistant (AI助手模块)

**职责**: 面向用户的智能问答服务，支持多轮对话

**核心特性**:

1. **会话管理**
   - 首次请求自动生成`sessionId`
   - 后续请求携带`conversationId`保持上下文
   - 最多加载最近20轮历史对话

2. **上下文窗口管理**
   ```java
   // Token截断策略
   1. 保留系统提示(SystemMessage)
   2. 从后往前添加消息，直到接近限制
   3. 保留20%余量给当前回复
   ```

3. **内容安全**
   - 基础敏感词过滤
   - 输入长度限制
   - 异常响应处理

---

## 三、核心流程

### 3.1 RAG问答时序图

```
Frontend          StreamCtrl        RAGService     VectorService    LLMService
   |                  |                  |                |              |
   | POST /query      |                  |                |              |
   |----------------->|                  |                |              |
   |                  | ragQuery()       |                |              |
   |                  |----------------->|                |              |
   |                  |                  | checkIndex()   |              |
   |                  |                  |--------------- |              |
   |                  |                  | OK             |              |
   |                  |                  |                |              |
   |                  |                  | generateEmbedding()
   |                  |                  |------------------------------>|
   |                  |                  |                | vector       |
   |                  |                  | search(vector) |              |
   |                  |                  |--------------->|              |
   |                  |                  |   SearchResult[]             |
   |                  |                  |<---------------|              |
   |                  |                  |                |              |
   |                  |                  | buildRAGPrompt |              |
   |                  |                  | streamChat()   |              |
   |                  |                  |------------------------------>|
   |                  |                  |                |              |
   |                  | SSE: message     |                |              |
   |<-----------------| (token 1)        |                |              |
   |                  | SSE: message     |                |              |
   |<-----------------| (token 2)        |                |              |
   |                  | ...              |                |              |
   |                  | SSE: done        |                |              |
   |<-----------------|                  |                |              |
```

### 3.2 文档索引时序图

```
Frontend    DocumentCtrl    RAGService    RocketMQ    IndexingConsumer    DocProcessor    VectorService
  |              |              |             |              |                |              |
  | POST upload  |              |             |              |                |              |
  |------------->|              |             |              |                |              |
  |              | validate()   |             |              |                |              |
  |              | save file    |             |              |                |              |
  |              | save DB      |             |              |                |              |
  |              | send MQ      |             |              |                |              |
  |              |------------->|------------>|              |                |              |
  | 202 Accepted |              |             |              |                |              |
  |<-------------|              |             |              |                |              |
  |                             |             | onMessage()  |                |              |
  |                             |             |------------->|                |              |
  |                             |             |              | parse(Tika)    |              |
  |                             |             |              |--------------->|              |
  |                             |             |              | chunk()        |              |
  |                             |             |              | embed()        |              |
  |                             |             |              |----------------------------->|
  |                             |             |              | save vectors   |              |
  |                             |             |              | update DB      |              |
```

---

## 四、技术栈与选型

### 4.1 核心技术栈

| 层级 | 技术 | 版本 | 选型理由 |
|------|------|------|----------|
| 基础框架 | Spring Boot | 2.6.7 | 项目统一版本，兼容Java 17 |
| AI框架 | LangChain4j | 0.27.1 | 功能丰富，流式支持完善，社区活跃 |
| LLM | OpenAI API | - | 主流选择，LangChain4j原生支持 |
| 向量库 | Milvus | 2.3.4 | 高性能，支持海量向量，Java SDK完善 |
| 响应式 | Project Reactor | 3.4.x | Spring生态原生支持 |
| 文档解析 | Apache Tika | 2.9.1 | 支持多种格式，成熟稳定 |
| 消息队列 | RocketMQ | 4.9.x | 项目统一中间件 |
| 缓存 | JetCache | 2.7.5 | 多级缓存(本地+远程)，注解驱动 |
| 熔断降级 | Resilience4j | 1.7.1 | Spring Boot 2.x兼容 |
| 数据库 | MySQL | 8.0+ | 项目统一存储 |
| 缓存中间件 | Redis | 6.0+ | 项目统一缓存 |

### 4.2 为什么选LangChain4j而非Spring AI

| 对比项 | Spring AI | LangChain4j |
|--------|-----------|-------------|
| Spring Boot兼容性 | 需3.x | 支持2.x |
| 功能完整度 | 基础 | 丰富 |
| 流式输出 | 有限 | 完善 |
| 社区活跃度 | 较新 | 活跃 |
| 文档质量 | 一般 | 优秀 |
| 国内模型支持 | 较少 | 较多 |

### 4.3 Java 17兼容性说明

**关键配置**:
```xml
<!-- 根pom.xml -->
<properties>
    <java.version>17</java.version>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
</properties>
```

**注意事项**:
- Spring Boot 2.6.7 内置ASM解析器不支持Java 21 bytecode (major 65)
- 使用JDK 21运行环境时，编译目标必须保持为17
- `javax.annotation`包通过显式依赖保障兼容性:
  ```xml
  <dependency>
      <groupId>javax.annotation</groupId>
      <artifactId>javax.annotation-api</artifactId>
      <version>1.3.2</version>
  </dependency>
  ```

---

## 五、接口设计

### 5.1 AI助手接口

#### 智能问答（流式）

```http
POST /api/ai/assistant/question
Content-Type: application/json
Accept: text/event-stream

Request:
{
    "userId": 10001,
    "question": "如何重置密码？",
    "conversationId": "conv_abc123",  // 可选，首次请求不传
    "context": "用户来自帮助中心"       // 可选，附加背景
}

Response (SSE):
event: session
data: conv_abc123

event: message
data: 您

event: message
data: 可以

event: message
data: 在设置页面重置密码...

event: done
data: 
```

#### 获取对话历史

```http
GET /api/ai/assistant/history?sessionId=conv_abc123

Response:
[
    {
        "id": 1,
        "userId": 10001,
        "sessionId": "conv_abc123",
        "conversationType": "QA",
        "userInput": "如何重置密码？",
        "aiResponse": "您可以在设置页面重置密码...",
        "responseTime": 2300,
        "createTime": "2026-05-17T10:30:00"
    }
]
```

### 5.2 RAG知识问答接口

#### 流式RAG查询

```http
POST /api/stream/rag/query
Content-Type: application/json
Accept: text/event-stream

Request:
{
    "userId": 10001,
    "question": "MallChat的缓存策略是什么？",
    "documentId": 123,        // 可选，限定特定文档
    "topK": 5                 // 检索片段数量
}

Response (SSE):
event: message
data: {"index":0,"type":"content","data":"MallChat"}

event: message
data: {"index":1,"type":"content","data":"采用"}

event: message
data: {"index":2,"type":"content","data":"多级缓存策略..."}

event: done
data: {"index":3,"type":"end"}
```

### 5.3 文档管理接口

#### 上传文档

```http
POST /api/documents/upload
Content-Type: multipart/form-data

Form Data:
- title: "产品使用手册"
- file: [文件内容]
- userId: 10001
- description: "可选描述"

Response:
{
    "documentId": 123,
    "title": "产品使用手册",
    "indexStatus": "PENDING",
    "message": "文档上传成功，正在等待索引处理"
}
```

#### 检查索引状态

```http
GET /api/documents/{documentId}/status

Response: "COMPLETED"  // PENDING / INDEXING / COMPLETED / FAILED
```

---

## 六、优化路线图

### Phase 1: 基础加固 (已完成)

- [x] Java 17兼容性修复
- [x] 循环依赖解决
- [x] LangChain4j迁移
- [x] 流式输出实现
- [x] 多轮对话支持
- [x] 文档处理流水线

### Phase 2: 多模型支持 (进行中)

- [ ] 通义千问(Qwen)接入
- [ ] DeepSeek接入
- [ ] 文心一言(ERNIE)接入
- [ ] 模型路由与负载均衡
- [ ] 模型性能对比与自动切换

### Phase 3: RAG增强

- [ ] 混合检索（向量+关键词BM25）
- [ ] 重排序(Rerank)优化
- [ ] 查询改写(Query Expansion)
- [ ] 多知识库隔离（按房间/用户）
- [ ] 检索结果引用溯源

### Phase 4: AI Agent框架

- [ ] 工具调用(Tool Calling)框架
- [ ] ReAct推理-行动循环
- [ ] 长期记忆系统（对话摘要）
- [ ] 智能体工作流编排
- [ ] 多Agent协作

### Phase 5: 工程优化

- [ ] PGVector轻量替代方案
- [ ] 调用链追踪
- [ ] Token消耗统计看板
- [ ] 模型响应质量评估
- [ ] 前端AI交互面板

---

## 七、AI Agent应用开发知识

### 7.1 什么是AI Agent

AI Agent是一种能够**自主感知环境、做出决策并执行动作**的智能系统。与传统的一次性问答不同，Agent具备：

1. **记忆(Memory)**: 短期记忆(对话上下文) + 长期记忆(知识库)
2. **工具(Tools)**: 调用外部API、执行代码、查询数据库
3. **规划(Planning)**: 将复杂任务拆解为子任务
4. **行动(Action)**: 根据规划执行具体操作

### 7.2 ReAct模式 (Reasoning + Acting)

```
用户提问: "北京今天天气怎么样？适合穿什么？"

Step 1 - Thought:
"用户问的是北京今天的天气和穿衣建议。我需要先获取天气信息。"

Step 2 - Action:
调用 weather_api(city="北京", date="今天")

Step 3 - Observation:
"北京今天晴天，温度15-25度，微风"

Step 4 - Thought:
"天气晴朗温暖，适合穿薄外套或长袖。我可以给出穿衣建议了。"

Step 5 - Final Answer:
"北京今天天气晴朗，温度15-25度。建议穿薄外套或长袖T恤，
  搭配长裤即可，不需要带雨伞。"
```

### 7.3 工具调用(Tool Calling)

```java
// 定义工具
public interface WeatherTool {
    @Tool("获取指定城市的天气信息")
    WeatherInfo getWeather(@ToolParam("城市名称") String city);
}

// Agent使用工具
public class AIAgent {
    public String answer(String question) {
        // 1. 分析用户意图，判断需要调用什么工具
        // 2. 调用工具获取信息
        // 3. 基于工具返回结果生成回答
    }
}
```

### 7.4 记忆系统设计

```
+---------------------+     +---------------------+     +---------------------+
|   短期记忆           |     |   中期记忆           |     |   长期记忆           |
|   (上下文窗口)       |     |   (对话摘要)         |     |   (知识库)           |
|                     |     |                     |     |                     |
| - 最近N轮对话        |     | - 会话主题           |     | - 用户画像           |
| - 当前问题           |     | - 关键信息           |     | - 历史偏好           |
| - 系统提示           |     | - 待办事项           |     | - 领域知识           |
|                     |     |                     |     |                     |
| 存储: 内存          |     | 存储: Redis/DB      |     | 存储: 向量数据库      |
| 容量: 4K-128K tokens|     | 容量: 最近10轮摘要   |     | 容量: 无限制         |
+---------------------+     +---------------------+     +---------------------+
```

### 7.5 本项目的Agent扩展方向

```
当前状态                    目标状态
----------                  ----------
单一LLM调用         -->     多模型路由
简单问答            -->     工具调用Agent
无状态对话          -->     持久化记忆
固定Prompt          -->     动态Prompt工程
单一RAG检索         -->     混合检索+重排序
```

---

*本文档由Claude Code辅助生成，后续将根据实际开发进度持续更新。*
