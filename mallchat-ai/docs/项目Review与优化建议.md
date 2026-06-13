# MallChat 项目 Review 与优化建议

> **Review范围**: Java后端（mallchat-chat-server + mallchat-ai + mallchat-tools）  
> **Review日期**: 2026-06-13  
> **Review方式**: 静态代码分析 + 架构审视  
> **建议原则**: 只说明优化方向，先不改代码，等确认后再实施  
> **版本**: v1.1（随 AI 模块迁移到 Ollama+Qdrant 方案更新）

---

## 目录

1. [总体印象](#1-总体印象)
2. [架构层面优化](#2-架构层面优化)
3. [代码层面优化](#3-代码层面优化)
4. [性能层面优化](#4-性能层面优化)
5. [工程实践优化](#5-工程实践优化)
6. [AI模块特定建议](#6-ai模块特定建议)
7. [优先级排序](#7-优先级排序)

---

## 1. 总体印象

### 1.1 项目优点

| 维度 | 评价 |
|------|------|
| **架构分层** | 模块划分清晰（chat/user/websocket/chatai），职责分离合理 |
| **技术选型** | Netty + WebSocket 实现实时通信，RocketMQ 异步解耦，选型成熟 |
| **缓存设计** | 多级缓存（Caffeine本地 + Redis远程），JetCache 统一管理，设计到位 |
| **代码规范** | 注释详细，类职责单一，命名较规范 |
| **扩展性** | 策略模式（MsgHandlerFactory、MsgMarkFactory）、适配器模式运用得当 |
| **AI模块** | 已完成从 OpenAI/Milvus 到 Ollama/Qdrant 的本地开源迁移，Mock 模式完整 |

### 1.2 主要问题域

| 问题域 | 严重程度 | 说明 |
|--------|---------|------|
| 循环依赖风险 | ⚠️ 中 | Service 层相互注入较多 |
| 事务边界模糊 | ⚠️ 中 | 部分业务逻辑事务范围过大 |
| 异常处理不一致 | ⚠️ 中 | 部分代码吞异常，部分抛运行时异常 |
| 重复代码 | ⚠️ 低 | 部分 DAO 层操作可抽象 |
| 缺少单元测试 | ⚠️ 中 | 核心业务逻辑测试覆盖不足 |
| Spring Boot版本偏旧 | ⚠️ 低 | 2.7.x，建议升级到 3.x |
| 文档滞后 | ⚠️ 中 | 部分文档未及时随代码更新（正在逐步补齐） |

---

## 2. 架构层面优化

### 2.1 循环依赖治理

**现状**:
```java
// ChatServiceImpl 注入 ContactService
@Service
public class ChatServiceImpl implements ChatService {
    @Autowired private ContactService contactService;
}

// 而 ContactService 的实现又可能依赖 ChatService
// 形成隐式循环依赖
```

**优化建议**:
1. **事件驱动解耦**: 将跨域操作改为 Spring Event 发布/订阅
   ```java
   // 替代直接调用 contactService.xxx()
   applicationEventPublisher.publishEvent(new ContactRefreshEvent(roomId, uidList));
   ```
2. **提取公共服务**: 将双方依赖的逻辑提取到独立的 `ContactSyncService`
3. **延迟注入**: 使用 `@Lazy` 注解打破启动时循环依赖（临时方案）

---

### 2.2 领域模型贫血

**现状**:
```java
// Entity 只有 getter/setter，业务逻辑全在 Service
@Entity
public class Message {
    private Long id;
    private String content;
    // ... 只有字段，没有行为
}

// 业务逻辑全在 Service
public void sendMessage(...) {
    // 校验、转换、存储全部在这里
}
```

**优化建议**:
1. **充血模型**: 将领域行为下沉到 Entity
   ```java
   public class Message {
       public boolean canRecall(User operator) {
           return this.senderUid.equals(operator.getId()) 
               && System.currentTimeMillis() - createTime < 120000;
       }
   }
   ```
2. **值对象**: 将 `WSBaseReq`、`ChatMessageResp` 等 DTO 中纯数据部分提炼为值对象

---

### 2.3 消息推送架构优化

**现状**:
```
MsgSendConsumer -> 判断房间类型 -> 查询成员 -> 逐个推送
```

**问题**: 热门房间（如全员群）一条消息需要推送给几万人，Consumer 执行时间超长

**优化建议**:
1. **批量推送**: 将成员分批，每批 500 人，异步并行推送
2. **读写分离**: Consumer 只负责"写扩散"（写入每个用户的收件箱），推送由独立的 Pull 服务处理
3. **优先级队列**: 活跃用户优先推送，冷用户延迟推送

---

### 2.4 WebSocket 连接管理

**现状**:
```java
// 使用 ConcurrentHashMap 存储在线连接
private static final ConcurrentHashMap<Channel, WSChannelExtraDTO> ONLINE_WS_MAP = ...;
```

**优化建议**:
1. **分布式场景**: 如果部署多实例，ConcurrentHashMap 无法跨 JVM 共享，需要改为 Redis 存储在线状态
2. **连接数上限**: 当前无连接数限制，需增加 `max-connections` 保护
3. **Channel 生命周期**: `WAIT_LOGIN_MAP` 使用 Caffeine 过期，但 `ONLINE_WS_MAP` 无自动清理，断网时可能内存泄漏

---

## 3. 代码层面优化

### 3.1 异常处理统一

**现状问题**:
```java
// 问题1: 吞异常
public void someMethod() {
    try {
        // ...
    } catch (Exception e) {
        // 空catch，异常信息丢失！
    }
}

// 问题2: 抛通用RuntimeException
throw new RuntimeException("Failed to xxx");

// 问题3: 部分用 AssertUtil，部分手动判断
AssertUtil.isNotEmpty(list, "列表不能为空");
if (obj == null) throw new BusinessException("对象为空");
```

**优化建议**:
1. **统一异常体系**: 定义业务异常枚举
   ```java
   public enum ErrorEnum {
       PARAM_INVALID(400, "参数错误"),
       MSG_NOT_FOUND(404, "消息不存在"),
       NO_PERMISSION(403, "无权限操作");
   }
   ```
2. **全局异常处理器**: 已有的 `AIGlobalExceptionHandler` 模式推广到整个项目
3. **禁止空catch**: 使用 SonarLint / Checkstyle 强制检查

---

### 3.2 DAO 层重复代码抽象

**现状**:
```java
// 每个 DAO 都有类似的 getById、listByIds 方法
public class MessageDao extends ServiceImpl<MessageMapper, Message> {
    public Message getById(Long id) { return lambdaQuery().eq(Message::getId, id).one(); }
}

public class RoomDao extends ServiceImpl<RoomMapper, Room> {
    public Room getById(Long id) { return lambdaQuery().eq(Room::getId, id).one(); }
}
```

**优化建议**:
1. **基础 DAO**: 提取公共方法到 `BaseDao`
   ```java
   public class BaseDao<M extends BaseMapper<T>, T> extends ServiceImpl<M, T> {
       public T getById(Long id) { ... }
       public List<T> listByIds(Collection<Long> ids) { ... }
   }
   ```

---

### 3.3 魔法值治理

**现状**:
```java
// 魔法值 scattered 在代码中
if (room.isHotRoom()) { ... }  // "热门房间"的判断逻辑散落
roomDao.refreshActiveTime(room.getId(), message.getId(), message.getCreateTime());
```

**优化建议**:
1. **常量集中**: 将业务常量抽取到 `ChatConstants`
2. **枚举强化**: 用枚举替代 boolean 标志
   ```java
   public enum RoomHotTypeEnum {
       HOT(1, "热门房间", 10000),    // 超过1万人
       NORMAL(0, "普通房间", 500);   // 普通群
   }
   ```

---

### 3.4 日志规范

**现状**:
```java
// 部分地方用字符串拼接（性能差）
log.info("用户" + uid + "发送消息" + msgId);

// 部分地方用占位符（正确）
log.info("用户{}发送消息{}", uid, msgId);
```

**优化建议**:
1. **统一使用占位符**: 避免字符串拼接
2. **敏感信息脱敏**: 日志中不打印 token、密码等
3. **链路追踪**: 引入 MDC 或 SkyWalking traceId，方便排查问题

---

## 4. 性能层面优化

### 4.1 数据库查询优化

**现状问题**:
```java
// MsgSendConsumer 中
List<Long> memberUidList = groupMemberCache.getMemberUidList(room.getId());
// 热门房间可能有 10000+ 成员，一次性查询压力大
```

**优化建议**:
1. **分页查询**: 大列表分批处理
   ```java
   // 分批处理，每批 500 人
   Lists.partition(memberUidList, 500).forEach(batch -> {
       // 处理 batch
   });
   ```
2. **覆盖索引**: 检查 `message` 表的 `(room_id, create_time)` 是否有联合索引
3. **读写分离**: 查询走从库，写入走主库

---

### 4.2 缓存优化

**现状**:
```java
// RoomCache 用 JetCache，但缺少缓存预热
public class RoomCache {
    @Cache(name = "room", key = "#roomId")
    public Room getRoom(Long roomId) { ... }
}
```

**优化建议**:
1. **缓存预热**: 启动时预加载热门房间数据
2. **缓存一致性**: 当前用 `delete` 失效缓存，可改为 **Cache Aside + 延迟双删**
3. **本地缓存限制**: Caffeine 的 `maximumSize` 建议根据内存调整，当前 100 可能偏小

---

### 4.3 异步化

**现状**:
```java
// MsgSendConsumer 是同步处理
public void onMessage(MsgSendMessageDTO dto) {
    // 更新房间、更新会话、推送消息...全部同步
}
```

**优化建议**:
1. **内部异步**: Consumer 内使用 `@Async` 将推送和存储解耦
2. **批量消费**: RocketMQ 开启批量消费模式，提升吞吐

---

## 5. 工程实践优化

### 5.1 版本升级

| 组件 | 当前版本 | 建议版本 | 升级收益 |
|------|---------|---------|---------|
| Spring Boot | 2.7.x | 3.2.x | 性能提升、安全补丁、原生镜像支持 |
| MyBatis-Plus | 3.4.0 | 3.5.5 | 新特性、bug修复 |
| Netty | 4.1.76 | 4.1.107 | 性能优化、安全修复 |
| JDK | 17 | 17（保持） | LTS版本，无需升级 |
| LangChain4j | 0.36.0 | 0.36.0（保持） | 已升级到支持 Ollama/Qdrant 的版本 |

**注意**: Spring Boot 2.x -> 3.x 是重大升级（ Jakarta EE 命名空间变化），建议先评估影响范围

---

### 5.2 测试覆盖

**现状**: 缺少单元测试和集成测试

**优化建议**:
1. **核心逻辑单元测试**: `ChatServiceImpl.sendMsg()`、`MsgSendConsumer.onMessage()`
2. **AI模块单元测试**: `RAGServiceImpl.ragQuery()`、`AIAssistantServiceImpl.answerQuestion()`
3. **Mock模式测试**: 利用 Mock 实现编写不依赖外部服务的测试
4. **契约测试**: WebSocket 消息格式的 JSON Schema 校验
5. **性能测试**: JMH 测试热点方法（如消息序列化）

---

### 5.3 配置管理

**现状**:
```yaml
# application.yml 中配置分散
wx:
  mp:
    callback: ${mallchat.wx.callback}
    configs:
      - appId: ${mallchat.wx.appId}
```

**优化建议**:
1. **配置中心化**: 使用 Nacos / Apollo 管理配置
2. **配置加密**: 数据库密码、API Key 等使用 Jasypt 加密
3. **配置校验**: 使用 `@Validated` + `@ConfigurationProperties` 启动时校验

---

### 5.4 监控告警

**现状**: 有 Actuator + Prometheus，但缺少业务指标

**优化建议**:
1. **业务指标**: 消息发送 QPS、WebSocket 在线数、消息延迟分位值、AI 查询 QPS
2. **AI业务指标**: 向量检索耗时、LLM 调用耗时、Embedding 批量耗时、缓存命中率
3. **健康检查**: 自定义 HealthIndicator 检查 Redis、MySQL、RocketMQ、Ollama、Qdrant
4. **告警规则**: Prometheus Alertmanager 配置告警（如消息堆积 > 1000）

---

## 6. AI模块特定建议

### 6.1 与原有chatai模块的整合

**现状**:
```
mallchat-chat-server/common/chatai   # 旧AI（ChatGPT/ChatGLM2）
mallchat-ai/                          # 新AI（RAG + LLM）
```

**问题**: 两套 AI 代码并存，逻辑分散

**优化建议**:
1. **统一入口**: 将 `chatai` 包迁移到 `mallchat-ai` 模块 ✅（部分已完成）
2. **统一配置**: 合并 `ChatGPTProperties` / `ChatGLM2Properties` 到 `LLMConfig` ✅（建议继续推进）
3. **统一接口**: `IChatAIService` 与 `LLMService` 合并 ✅（建议继续推进）

**状态**: 新 AI 模块已独立成体系，旧 `chatai` 建议逐步下线或做兼容转发。

---

### 6.2 RAG检索性能

**现状**: RAG 查询需要 3 次网络调用（Embedding -> 向量检索 -> LLM）

**优化建议**:
1. **并发调用**: Embedding 和 上下文准备并行
2. **缓存查询结果**: 相同问题的向量检索结果缓存 5 分钟（已实现 `QueryResultCache`）
3. **降级策略**: 向量库不可用时，直接走 LLM（已实现 `fallbackToNormalQA`）
4. **混合检索**: 引入 BM25 关键词检索，与向量检索结果融合

---

### 6.3 文档处理

**现状**: 文档分块、Embedding、存储串行执行

**优化建议**:
1. **流水线并行**: 分块 -> Embedding（批量）-> 存储 流水线化
2. **进度通知**: 大文档处理耗时，通过 WebSocket 推送进度给用户
3. **批量索引**: 当前 `DocumentIndexingConsumer` 已支持批量，可进一步优化批次大小

---

### 6.4 向量库长期演进

**现状**: Qdrant 作为默认向量库，Milvus 作为备选

**优化建议**:
1. **短中期**: 继续使用 Qdrant，利用动态向量优势
2. **长期**: 如果数据量达到千万级以上，评估迁移到 Milvus 或引入 PGVector
3. **多向量库抽象**: `VectorService` 接口已抽象，迁移成本可控

---

## 7. 优先级排序

### P0（建议本周做）

| 优化项 | 原因 |
|--------|------|
| 1. Mock模式验证启动 | 确保代码能跑通 ✅（已完成） |
| 2. 统一异常处理 | 影响系统稳定性 |
| 3. 空catch检查 | 隐藏bug风险 |
| 4. 文档同步更新 | 防止知识滞后 ✅（本次已更新核心文档） |

### P1（建议本月做）

| 优化项 | 原因 |
|--------|------|
| 4. 循环依赖治理 | 影响代码可维护性 |
| 5. 热门房间批量推送 | 性能瓶颈 |
| 6. 基础DAO抽象 | 减少重复代码 |
| 7. AI模块整合 | 消除代码冗余（持续推进） |
| 8. AI模块单元测试 | 保证质量 |

### P2（建议三个月内）

| 优化项 | 原因 |
|--------|------|
| 8. Spring Boot升级 | 技术债 |
| 9. 单元测试覆盖 | 质量保证 |
| 10. 监控告警完善 | 运维能力 |
| 11. 配置中心化 | 工程规范 |
| 12. 混合检索 | RAG效果提升 |

### P3（长期规划）

| 优化项 | 原因 |
|--------|------|
| 12. 领域模型充血化 | 架构演进 |
| 13. 读写分离 | 性能扩展 |
| 14. 多活部署 | 高可用 |
| 15. AI Agent框架 | 功能演进 |

---

*本文档由 AI Assistant 生成，建议团队讨论后按优先级实施。*
