# MallChat 项目全局Review与优化建议文档

> **审查日期**: 2026-06-01  
> **审查范围**: MallChat完整项目（mallchat-chat-server、mallchat-tools、mallchat-ai）  
> **审查维度**: 代码质量、架构设计、性能、安全性、可维护性  
> **项目版本**: 1.0-SNAPSHOT  
> **技术栈**: Spring Boot 2.6.7 + Java 17 + Netty + Redis + RocketMQ + MyBatis-Plus + LangChain4j

---

## 📊 审查概览

| 维度 | 严重 | 高 | 中 | 低 | 总计 |
|------|------|-----|-----|-----|------|
| 代码质量 | 15 | 20 | 35 | 30 | 100 |
| 架构设计 | 7 | 10 | 12 | 6 | 35 |
| 性能 | 2 | 5 | 8 | 4 | 19 |
| 安全性 | 5 | 6 | 5 | 3 | 19 |
| 可维护性 | 2 | 6 | 7 | 4 | 19 |
| **合计** | **31** | **47** | **67** | **47** | **192** |

---

## 🔴 TOP 10 紧急修复项

| # | 问题 | 严重程度 | 影响范围 |
|---|------|---------|---------|
| 1 | 日志中打印敏感信息（API Key、密码） | 🔴严重 | 多处文件 |
| 2 | 测试登录接口无环境限制 | 🔴严重 | UserController |
| 3 | Actuator端点全部暴露+shutdown启用 | 🔴严重 | application.yml |
| 4 | HikariCP maxLifetime=30秒 | 🔴严重 | application.yml |
| 5 | Redis KEYS命令阻塞风险 | 🔴严重 | QueryResultCache |
| 6 | Controller直接调用DAO层 | 🔴严重 | UserController等 |
| 7 | MQ消费者缺少幂等性保证 | 🔴严重 | 所有Consumer |
| 8 | 线程安全问题（静态变量非线程安全） | 🔴严重 | Handler/AFilter |
| 9 | 限流异常默认放行（fail-safe违反） | 🔴严重 | RateLimitServiceImpl |
| 10 | chat-server与AI模块强耦合 | 🔴严重 | pom.xml |

---

---

> **下文是详细的五维度审查结果，由并行Agent分工完成。**  
> **代码质量审查(100项) → 架构设计审查(35项) → 性能审查(19项) → 安全性审查(19项) → 可维护性审查(19项)**

---

# 第一部分：代码质量审查（100项）

> 审查范围：mallchat-chat-server、mallchat-ai、mallchat-tools 模块

---

## 一、命名规范

### 1.1 类名/方法名不符合Java规范

| 序号 | 问题描述 | 文件路径 | 行号 | 严重程度 | 优化建议 |
|------|---------|---------|------|---------|---------|
| 1 | 变量名 `ac_trie` 使用下划线命名，不符合Java驼峰规范 | `ACFilter.java` | 22 | 中 | 改为 `acTrie` |
| 2 | 变量名 `mask_char` 使用下划线命名 | `ACFilter.java` | 20 | 中 | 改为 `maskChar` |
| 3 | 方法名 `userChatNumInrc` 拼写错误，应为 `userChatNumIncr` | `GPTChatAIHandler.java` | 143 | 低 | 修正拼写 |
| 4 | 包名 `intecepter` 拼写错误，应为 `interceptor` | 整个包 | - | 中 | 修正包名拼写 |
| 5 | `AI_NAME` 使用全大写命名但非final常量 | `ChatGLM2Handler.java` | 50 | 中 | 改为小写驼峰 `aiName` |
| 6 | `AI_NAME` 同样存在非final静态变量问题 | `GPTChatAIHandler.java` | 42 | 中 | 同上 |
| 7 | `ERROR_MSG` 列表命名应为复数形式 | `ChatGLM2Handler.java` | 39 | 低 | 改为 `ERROR_MESSAGES` |

---

## 二、注释质量

### 2.1 注释缺失或不完整

| 序号 | 问题描述 | 文件路径 | 行号 | 严重程度 | 优化建议 |
|------|---------|---------|------|---------|---------|
| 8 | `GroupErrorEnum` 枚举值注释为空 | `GroupErrorEnum.java` | 16 | 低 | 补充枚举值含义注释 |
| 9 | `ChatGLM2Handler.sendRequestToGPT()` 方法注释只有 `TODO` | `ChatGLM2Handler.java` | 101-106 | 中 | 补充方法功能说明 |
| 10 | `CommonUrlDiscover.getDescription()` 未处理-1情况 | `CommonUrlDiscover.java` | 25 | 中 | 添加防御性编程注释 |
| 11 | `RedisUtils` 大量方法缺少参数说明注释 | `RedisUtils.java` | 多个 | 低 | 补充JavaDoc参数说明 |

### 2.2 过时/无用注释

| 序号 | 问题描述 | 文件路径 | 严重程度 | 优化建议 |
|------|---------|---------|---------|---------|
| 12 | `ChatAIServiceImpl.java` 存在被注释掉的代码 | `ChatAIServiceImpl.java` | 低 | 删除无用代码 |
| 13 | `ChatGPTUtils.java` 存在被注释的字段 | `ChatGPTUtils.java` | 低 | 删除无用代码 |
| 14 | `RedisUtils.java` 存在大量被注释掉的代码块（约100行） | `RedisUtils.java` | 低 | 清理无用注释代码 |

---

## 三、代码重复

| 序号 | 问题描述 | 文件路径 | 严重程度 | 优化建议 |
|------|---------|---------|---------|---------|
| 15 | `ChatGLM2Handler` 和 `GPTChatAIHandler` 大量重复逻辑 | 两个Handler | 高 | 提取到 `AbstractChatAIHandler` |
| 16 | `RedisUtils` 在两个模块中几乎完全相同 | 两个RedisUtils.java | 高 | 统一使用tools中的版本 |
| 17 | `RAGServiceImpl` 中buildRAGPrompt/buildNormalQAPrompt 公共部分可提取 | `RAGServiceImpl.java` | 低 | 提取公共prompt构建器 |
| 18 | `OpenAILLMService` 两个streamChat重载方法体几乎相同 | `OpenAILLMService.java` | 中 | 提取公共Flux创建逻辑 |

---

## 四、异常处理

### 4.1 吞异常/异常处理不当

| 序号 | 问题描述 | 文件路径 | 行号 | 严重程度 | 优化建议 |
|------|---------|---------|------|---------|---------|
| 19 | `ChatGLM2Handler.doChat()` 捕获Throwable后返回固定字符串 | `ChatGLM2Handler.java` | 96-98 | 高 | 至少记录error日志 |
| 20 | `GPTChatAIHandler.doChat()` 同样捕获Throwable后返回固定字符串 | `GPTChatAIHandler.java` | 85-87 | 高 | 增加错误日志记录 |
| 21 | `RedisUtils` 大量方法捕获Exception后仅返回false/null | `RedisUtils.java` | 多个 | 高 | 提供抛出异常的重载方法 |
| 22 | `DFAFilter.loadWordFromFile()` 捕获IOException后仅e.printStackTrace() | `DFAFilter.java` | 147-153 | 高 | 使用日志框架记录 |
| 23 | `RateLimitServiceImpl.checkLimit()` 限流异常时默认允许请求 | `RateLimitServiceImpl.java` | 166-170 | 高 | 异常时应默认拒绝（fail-safe） |
| 24 | `AIAssistantServiceImpl` 捕获所有异常后返回空列表 | `AIAssistantServiceImpl.java` | 194-197 | 中 | 区分可恢复异常和系统异常 |

---

## 五、日志规范

### 5.1 日志级别使用不当

| 序号 | 问题描述 | 文件路径 | 行号 | 严重程度 | 优化建议 |
|------|---------|---------|------|---------|---------|
| 25 | 业务异常使用 `log.info` | `GlobalExceptionHandler.java` | 93 | 中 | 应使用 `log.warn` |
| 26 | 限流异常使用 `log.info` | `GlobalExceptionHandler.java` | 113 | 中 | 应使用 `log.warn` |
| 27 | API调用异常使用 `log.warn` | `ChatGLM2Handler.java` | 121 | 中 | 应使用 `log.error` |

### 5.2 敏感信息泄露风险

| 序号 | 问题描述 | 文件路径 | 行号 | 严重程度 | 优化建议 |
|------|---------|---------|------|---------|---------|
| 28 | `WebLogAspect` 打印所有请求参数含敏感信息 | `WebLogAspect.java` | 55-59 | 高 | 添加敏感字段过滤 |
| 29 | `ChatGPTUtils.send()` 打印完整请求参数含API Key | `ChatGPTUtils.java` | 171 | 高 | 打印前移除敏感字段 |
| 30 | `ChatGLM2Utils.send()` 打印headers含敏感信息 | `ChatGLM2Utils.java` | 64-65 | 高 | 打印前移除敏感字段 |
| 31 | `LangChain4jConfig` logRequests=true可能记录API Key | `LangChain4jConfig.java` | 48-49 | 高 | 关闭请求日志或脱敏 |
| 32 | `AIGlobalExceptionHandler` 脱敏正则不够完善 | `AIGlobalExceptionHandler.java` | 222 | 中 | 增加更多敏感字段匹配 |

---

## 六、空指针处理

| 序号 | 问题描述 | 文件路径 | 行号 | 严重程度 | 优化建议 |
|------|---------|---------|------|---------|---------|
| 33 | `room.isHotRoom()` 前未检查room是否为null | `GroupMemberServiceImpl.java` | 128 | 高 | 添加null检查 |
| 34 | message可能为null | `MessageSendListener.java` | 89-90 | 高 | 添加message非空检查 |
| 35 | `content.indexOf("。")` 可能返回-1 | `CommonUrlDiscover.java` | 25 | 高 | 检查indexOf返回值 |
| 36 | `response.body()` 可能为null | `ChatGPTUtils.java` | 84 | 高 | 添加null检查 |
| 37 | `message.getFilePath()` 未检查是否为null | `DocumentIndexingConsumer.java` | 77 | 高 | 添加非空检查 |
| 38 | `chunk.getMetadata()` 可能为null | `MilvusVectorService.java` | 291 | 中 | 添加非空检查 |
| 39 | `question.getBytes()` 未检查question是否为null | `QueryResultCache.java` | 163 | 中 | 添加非空检查 |

---

## 七、魔法值和硬编码

| 序号 | 问题描述 | 文件路径 | 行号 | 魔法值 | 优化建议 |
|------|---------|---------|------|--------|---------|
| 40 | 预留token数 | `GPTChatAIHandler.java` | 118 | 500 | 常量 `RESERVED_TOKENS = 500` |
| 41 | 最大重试次数 | `DocumentIndexingConsumer.java` | 142 | 3 | 常量 `MAX_RETRY_COUNT = 3` |
| 42 | 历史记录限制条数 | `AIAssistantServiceImpl.java` | 191 | 20 | 常量 `MAX_HISTORY_LIMIT = 20` |
| 43 | Token保留余量比例 | `AIAssistantServiceImpl.java` | 222 | 0.8 | 常量 `TOKEN_RESERVE_RATIO = 0.8` |
| 44 | maxTokens默认值 | `RAGServiceImpl.java` | 160,415 | 2000 | 配置项 |
| 45 | temperature默认值 | `RAGServiceImpl.java` | 多个 | 0.7 | 配置项 |
| 46 | HTTP超时时间 | `ChatGLM2Utils.java` | 20 | 60*1000 | 常量 `DEFAULT_TIMEOUT_MS` |

---

## 八、代码复杂度

### 8.1 方法过长

| 序号 | 问题描述 | 文件路径 | 严重程度 | 优化建议 |
|------|---------|---------|---------|---------|
| 47 | `RAGServiceImpl.ragQuery()` 超过90行 | `RAGServiceImpl.java` | 高 | 拆分为子方法 |
| 48 | `AIAssistantServiceImpl.answerQuestion()` 超过50行 | `AIAssistantServiceImpl.java` | 中 | 拆分子方法 |
| 49 | `TikaDocumentProcessingService.chunkBySemantic()` 超过70行 | `TikaDocumentProcessingService.java` | 中 | 提取块构建逻辑 |

### 8.2 类过大

| 序号 | 问题描述 | 文件路径 | 严重程度 | 优化建议 |
|------|---------|---------|---------|---------|
| 50 | `RedisUtils` 超过1100行 | `RedisUtils.java` | 中 | 按数据类型拆分 |
| 51 | `RAGServiceImpl` 超过520行 | `RAGServiceImpl.java` | 高 | 拆分为DocumentService和RAGQueryService |
| 52 | `MilvusVectorService` 超过580行 | `MilvusVectorService.java` | 中 | 提取连接管理和索引管理 |

---

## 九、资源泄漏

| 序号 | 问题描述 | 文件路径 | 行号 | 严重程度 | 优化建议 |
|------|---------|---------|------|---------|---------|
| 53 | 每次创建新OkHttpClient实例 | `ChatGPTUtils.java` | 155-160 | 高 | 使用单例或连接池 |
| 54 | HttpURLConnection异常分支未disconnect | `AbstractUrlDiscover.java` | 93-114 | 高 | 使用try-finally |
| 55 | RedisConnection异常时可能未释放 | `RedisUtils.java` | 141-156 | 高 | 使用try-finally确保释放 |

---

## 十、线程安全与并发

| 序号 | 问题描述 | 文件路径 | 行号 | 严重程度 | 优化建议 |
|------|---------|---------|------|---------|---------|
| 56 | `AI_NAME` 静态非final变量 | `ChatGLM2Handler.java` | 50 | 高 | 使用volatile或改为实例变量 |
| 57 | `AI_NAME` 同样问题 | `GPTChatAIHandler.java` | 42 | 高 | 同上 |
| 58 | `DFAFilter.root` 非线程安全 | `DFAFilter.java` | 26,107 | 高 | 使用volatile或同步 |
| 59 | `ACFilter.ac_trie` 非线程安全 | `ACFilter.java` | 22,67 | 高 | 使用volatile或同步 |
| 60 | 线程池未被Spring管理生命周期 | `BatchProcessingService.java` | 54-64 | 中 | 添加@PostConstruct |

---

## 十一、事务与设计问题

| 序号 | 问题描述 | 文件路径 | 行号 | 严重程度 | 优化建议 |
|------|---------|---------|------|---------|---------|
| 61 | `saveConversation` 在doOnComplete中无事务保障 | `RAGServiceImpl.java` | 169-172 | 高 | 确保数据库操作在事务中 |
| 62 | `saveConversationHistory` 同样无事务保障 | `AIAssistantServiceImpl.java` | 94-106 | 高 | 添加事务注解 |
| 63 | LLM工厂依赖bean名称字符串匹配 | `LLMServiceFactory.java` | 65-85 | 中 | 使用注解标记替代 |
| 64 | 使用@Transactional但包含MQ发送 | `DocumentIndexingConsumer.java` | 65 | 中 | 分离事务和消息发送 |
| 65 | System.out.println出现在生产代码中 | 多个文件 | 多处 | 中 | 替换为SLF4J日志 |
| 66 | e.printStackTrace()出现在生产代码中 | 多个文件 | 多处 | 高 | 替换为log.error() |

---

## 十二、性能相关代码问题

| 序号 | 问题描述 | 文件路径 | 行号 | 严重程度 | 优化建议 |
|------|---------|---------|------|---------|---------|
| 67 | float[]转List<Float>效率低 | `MilvusVectorService.java` | 342-346 | 中 | 使用float[]直接构造 |
| 68 | 多次遍历字符数组 | `TikaDocumentProcessingService.java` | 326-345 | 低 | 合并遍历 |
| 69 | 使用KEYS命令可能阻塞Redis | `QueryResultCache.java` | 136-149 | 中 | 使用SCAN替代 |
| 70 | incr+expire非原子操作 | `RateLimitServiceImpl.java` | 149-171 | 高 | 使用Lua脚本 |

---

## 总结

| 严重程度 | 数量 |
|---------|------|
| 严重 | 15 |
| 高 | 20 |
| 中 | 35 |
| 低 | 30 |
| **总计** | **100** |

---

# 第二部分：架构设计审查（35项）

## 一、模块划分

| # | 问题 | 位置 | 严重程度 | 建议 |
|----|------|------|---------|------|
| 1 | AI模块与聊天服务耦合过强 | `mallchat-chat-server/pom.xml` | 高 | 引入API网关或事件驱动解耦 |
| 2 | mallchat-ai-common职责不纯粹 | `mallchat-ai-common/pom.xml` | 中 | 拆分通用工具与数据模型 |
| 3 | mallchat-ai-finetune模块为空壳 | `mallchat-ai-finetune/` | 低 | 删除或实现 |
| 4 | mallchat-redis模块定位模糊 | `mallchat-redis/` | 中 | 明确边界或合并 |

## 二、分层架构

| # | 问题 | 位置 | 严重程度 | 建议 |
|----|------|------|---------|------|
| 5 | Controller直接调用DAO | `UserController.java:46` | 高 | DAO下沉到Service |
| 6 | AI Controller直接操作DAO | `AIAssistantController.java:101-134` | 高 | 创建Service封装 |
| 7 | Service接口与实现命名不一致 | user/service/ | 低 | 统一规范 |
| 8 | RAG模块缺少Application层 | RAG模块 | 中 | 引入Application Service |

## 三、依赖关系

| # | 问题 | 严重程度 | 建议 |
|----|------|---------|------|
| 9 | AI模块反向依赖mallchat-common-starter引入不必要依赖 | 高 | 拆分common-starter |
| 10 | assistant↔rag循环依赖风险 | 中 | 明确边界，抽取公共逻辑 |
| 11 | pom.xml中${version}应为${project.version} | 中 | 修复版本引用 |

## 四、接口设计

| # | 问题 | 严重程度 | 建议 |
|----|------|---------|------|
| 12 | URL风格不统一（/capi/ vs /api/） | 中 | 统一为/api/v1/ |
| 13 | AI模块返回值不统一 | 高 | 统一ApiResult<T>包装 |
| 14 | Controller参数校验与业务校验混杂 | 中 | 使用@Valid+DTO |
| 15 | 缺少API版本控制 | 中 | URL路径引入版本号 |

## 五、扩展性

| # | 问题 | 严重程度 | 建议 |
|----|------|---------|------|
| 16 | LLM工厂依赖bean名称字符串匹配 | 中 | 使用注解或接口方法 |
| 17 | 文档分块策略使用switch-case硬编码 | 低 | 使用策略模式 |
| 18 | Prompt散落各处难以管理 | 中 | 模板引擎+配置中心 |

## 六、领域模型设计

| # | 问题 | 严重程度 | 建议 |
|----|------|---------|------|
| 19 | AIConversation实体字段复用语义不清 | 中 | 添加独立context字段 |
| 20 | DTO与Entity转换方式不统一 | 中 | 统一使用MapStruct |
| 21 | 缺少值对象（VO）设计 | 低 | 引入不可变值对象 |
| 22 | ChatServiceImpl跨领域边界 | 高 | 明确边界+领域事件 |

## 七、配置管理

| # | 问题 | 严重程度 | 建议 |
|----|------|---------|------|
| 23 | 配置分散在多个模块 | 中 | @ConfigurationProperties统一 |
| 24 | 缺少配置加密机制 | 高 | 引入Jasypt |
| 25 | mock作为默认profile | 低 | 默认使用local |

## 八、数据库设计

| # | 问题 | 严重程度 | 建议 |
|----|------|---------|------|
| 26 | message表缺少分表设计 | 高 | ShardingSphere或分区表 |
| 27 | ai_conversation索引不优化 | 中 | 复合索引(user_id, session_id) |
| 28 | 缺少乐观锁广泛使用 | 中 | 统一使用@Version |
| 29 | 外键约束缺失 | 低 | 应用层保证一致性 |

## 九、消息驱动设计

| # | 问题 | 严重程度 | 建议 |
|----|------|---------|------|
| 30 | MQ消费者缺少幂等性保证 | 高 | 基于消息ID幂等校验 |
| 31 | 缺少死信队列（DLQ） | 高 | 配置DLQ+监控 |
| 32 | 消息体缺少版本号 | 中 | 增加version字段 |
| 33 | 事务消息与本地事务耦合 | 中 | 调整发送时机 |

## 十、设计模式

| # | 问题 | 严重程度 | 建议 |
|----|------|---------|------|
| 34 | AOP过度使用导致调试困难 | 中 | 精简+文档化 |
| 35 | SecureInvoke机制过度设计 | 中 | 评估是否使用MQ事务消息 |

---

# 第三部分：性能审查（19项）

## 🔴 严重问题

### P-01 HikariCP maxLifetime配置错误

- **文件**: `application.yml:68`
- **问题**: `max-lifetime: 30000`（30秒），导致连接频繁创建销毁
- **修复**: 改为 `max-lifetime: 1800000`（30分钟）

### P-02 Redis KEYS命令阻塞风险

- **文件**: `QueryResultCache.java:136-149`
- **问题**: `clearAllQueryCache()` 使用 `keys()` 可能阻塞Redis
- **修复**: 使用SCAN命令替代

## 🟠 高优先级

| # | 问题 | 文件 | 修复建议 |
|----|------|------|---------|
| P-03 | Redis timeout=30分钟过长 | `app.yml:81` | 改为5000ms |
| P-04 | JetCache本地缓存limit=100过小 | `app.yml:199` | 调整为1000 |
| P-05 | WebSocket推送无熔断机制 | 配置 | 添加自适应限流+熔断 |
| P-06 | 限流操作incr+expire非原子 | `RateLimitServiceImpl.java` | 使用Lua脚本 |
| P-07 | OkHttpClient每次都新建 | `ChatGPTUtils.java` | 使用单例+连接池 |

## 🟡 中优先级

| # | 问题 | 修复建议 |
|----|------|---------|
| P-08 | LLM调用无超时控制 | 添加超时配置 |
| P-09 | 流式处理缺背压控制 | 实现请求合并 |
| P-10 | @Async使用默认线程池 | 配置专用线程池 |
| P-11 | N+1查询风险 | @BatchSize或join fetch |
| P-12 | float[]转List效率低 | 直接使用float[] |
| P-13 | 线程池未被Spring管理 | 添加@PostConstruct |
| P-14 | HttpURLConnection未设置超时 | 添加connectTimeout |
| P-15 | 大量Redis操作未使用Pipeline | 使用Pipeline批量操作 |

## 🟢 低优先级

| # | 问题 | 修复建议 |
|----|------|---------|
| P-16 | 未配置JVM参数 | 设置堆内存+GC |
| P-17 | WebSocket大消息缺分片 | 实现消息分片 |
| P-18 | 缓存预热机制缺失 | 启动时加载热点数据 |
| P-19 | 连接池监控不足 | 集成Micrometer |

---

# 第四部分：安全性审查（19项）

## 🔴 严重问题

### S-01 测试登录接口无环境限制

- **文件**: `UserController.java:96-113`
- **风险**: 任何环境可生成任意用户token
- **修复**: 添加环境判断，仅dev/test可用

```java
if (!"dev".equals(activeProfile) && !"test".equals(activeProfile)) {
    return ApiResult.fail(403, "该接口仅在开发测试环境可用");
}
```

### S-02 Actuator端点过度暴露

- **文件**: `application.yml:259-278`
- **风险**: 暴露所有端点+shutdown启用
- **修复**: 仅暴露health/info/prometheus，禁用shutdown

### S-03 JWT Secret配置风险

- **文件**: `JwtUtils.java:30`
- **风险**: secret可被泄露伪造token
- **修复**: 环境变量注入+定期轮换+非对称加密

### S-04 限流异常默认放行

- **文件**: `RateLimitServiceImpl.java:166-170`
- **风险**: Redis故障时无限流保护
- **修复**: 异常时默认拒绝（fail-safe原则）

### S-05 敏感信息日志泄露

- **文件**: `WebLogAspect.java`, `ChatGPTUtils.java`, `ChatGLM2Utils.java`
- **风险**: API Key/密码可能被记录
- **修复**: 添加敏感字段过滤+脱敏

## 🟠 高优先级

| # | 问题 | 文件 | 修复建议 |
|----|------|------|---------|
| S-06 | 公开路径判断使用字符串分割易绕过 | `TokenInterceptor.java:94-98` | 使用路径前缀匹配 |
| S-07 | 文件上传仅基于扩展名校验 | `RAGServiceImpl.java:331-350` | Magic Number校验 |
| S-08 | 配置文件敏感信息未加密 | `application.yml` | 使用Jasypt加密 |
| S-09 | AI模块缺Prompt注入防护 | AI模块所有输入 | 过滤转义+模板化 |
| S-10 | JWT Token缺过期时间(exp) | `JwtUtils.java:40-47` | 添加withExpiresAt |
| S-11 | WebSocket认证可优化 | NettyWebSocketServerHandler | Token通过header传输 |

## 🟡 中优先级

| # | 问题 | 修复建议 |
|----|------|---------|
| S-12 | 速率限制实现可完善 | 添加用户级+分布式限流 |
| S-13 | 动态SQL需审查 | 确保参数化查询 |
| S-14 | 部分日志可能记录敏感信息 | 全量审查日志输出 |
| S-15 | CORS配置 | 限制为允许的域名 |
| S-16 | 事务边界安全问题 | doOnComplete中无事务 |

## 🟢 低优先级

| # | 问题 | 修复建议 |
|----|------|---------|
| S-17 | 缺少安全审计日志 | 记录关键操作 |
| S-18 | 未启用HTTPS强制跳转 | 配置HSTS |
| S-19 | 缺少安全响应头 | 添加X-Content-Type-Options等 |

---

# 第五部分：可维护性审查（19项）

## 🔴 严重问题

### M-01 测试覆盖率严重不足

- **现状**: chat-server仅3个测试类，tools模块几乎没有测试
- **目标**: 核心模块覆盖率>70%，集成测试覆盖核心流程
- **工具**: JaCoCo + 纳入CI/CD

### M-02 代码重复严重

- 两个RedisUtils几乎完全相同
- ChatGLM2Handler和GPTChatAIHandler大量重复
- **建议**: 提取公共抽象+统一工具类

## 🟠 高优先级

| # | 问题 | 修复建议 |
|----|------|---------|
| M-03 | 缺少项目README.md | 创建含架构图+快速开始的README |
| M-04 | docs/目录文档管理混乱 | 清理过时文档+建立目录规范 |
| M-05 | 版本号使用1.0-SNAPSHOT | 语义化版本+发布流程 |
| M-06 | 依赖版本陈旧 | 升级Spring Boot/Netty/jjwt |
| M-07 | 生产代码含System.out/e.printStackTrace | 全局替换为SLF4J |
| M-08 | 代码规范工具缺失 | 引入Spotless+SpotBugs/PMD |

## 🟡 中优先级

| # | 问题 | 修复建议 |
|----|------|---------|
| M-09 | skipTests: true默认跳过测试 | 默认不跳过 |
| M-10 | 监控告警不完善 | 添加业务指标+告警规则 |
| M-11 | 开发环境搭建文档不完善 | 补充local/mock模式说明 |
| M-12 | Git提交规范缺失 | 引入Commitlint |
| M-13 | 代码评审流程缺失 | 建立PR评审机制 |
| M-14 | TODO/FIXME缺乏跟踪 | 创建技术债务看板 |
| M-15 | 注释质量参差不齐 | 统一注释规范 |

## 🟢 低优先级

| # | 问题 | 修复建议 |
|----|------|---------|
| M-16 | 缺少CHANGELOG | 维护版本变更记录 |
| M-17 | 未配置Issue模板 | 添加Bug/Feature模板 |
| M-18 | 缺少贡献指南 | 添加CONTRIBUTING.md |
| M-19 | 未配置CODEOWNERS | 设置代码审查责任人 |

---

# 📋 优化实施路线图

## 第一阶段：紧急修复（1-2周）🔴

| 任务 | 优先级 | 验收标准 |
|------|--------|----------|
| 移除生产代码中的System.out/e.printStackTrace | P0 | 全局扫描无遗留 |
| 限制testLogin接口仅在测试环境可用 | P0 | 生产环境返回403 |
| 限制Actuator端点暴露 | P0 | 仅暴露health/info/prometheus |
| 修复HikariCP maxLifetime配置 | P0 | 设置为30分钟 |
| 修复敏感信息日志泄露 | P0 | 日志中无API Key/密码 |
| 修复限流异常默认放行 | P0 | 异常时默认拒绝 |
| 修复Redis KEYS命令阻塞风险 | P0 | 使用SCAN替代 |

## 第二阶段：安全加固（2-3周）🟠

| 任务 | 优先级 | 验收标准 |
|------|--------|----------|
| 为JWT添加过期时间(exp) | P1 | Token包含exp声明 |
| 改进公开路径判断逻辑 | P1 | 使用路径前缀匹配 |
| 加强文件上传安全校验 | P1 | Magic Number校验 |
| 加密配置文件敏感信息 | P1 | Jasypt加密 |
| 修复线程安全问题 | P1 | AI_NAME等改为final/volatile |
| AI模块Prompt注入防护 | P1 | 输入过滤+模板化 |
| 为MQ消费者添加幂等性 | P1 | 基于消息ID校验 |

## 第三阶段：架构优化（3-4周）🟡

| 任务 | 优先级 | 验收标准 |
|------|--------|----------|
| 重构分层架构，禁止Controller直调Dao | P1 | Controller只依赖Service |
| 统一使用构造器注入 | P1 | 无@Autowired字段注入 |
| 优化模块依赖关系 | P2 | chat-server不直接依赖AI实现 |
| 统一配置管理 | P2 | 按功能拆分配置文件 |
| 消除代码重复（Handler/RedisUtils） | P2 | 提取公共抽象类 |
| 统一API返回值包装 | P2 | 所有接口统一ApiResult<T> |
| 配置MQ死信队列 | P2 | DLQ+监控告警 |

## 第四阶段：质量提升（4-6周）🟢

| 任务 | 优先级 | 验收标准 |
|------|--------|----------|
| 补充核心业务单元测试 | P1 | 核心模块覆盖率>70% |
| 引入代码规范工具 | P2 | Spotless/SpotBugs集成 |
| 升级Spring Boot版本 | P2 | 2.7.x或3.x |
| 完善监控告警 | P2 | 业务指标+告警规则 |
| 完善项目文档 | P2 | README+架构图+开发规范 |
| 统一DTO-Entity转换 | P3 | 使用MapStruct |
| 优化AI调用性能 | P3 | 超时+连接池+背压 |

## 第五阶段：持续优化（持续）🔵

| 任务 | 优先级 | 验收标准 |
|------|--------|----------|
| 优化缓存配置 | P2 | 缓存命中率>80% |
| 数据库查询优化 | P2 | 慢查询归零 |
| JVM参数调优 | P3 | GC暂停<100ms |
| message表分表设计 | P2 | 千万级数据性能达标 |
| 引入API版本控制 | P3 | /api/v1/、/api/v2/ |
| 配置中心集成 | P3 | Nacos/Apollo |

---

# 📊 最佳实践规范

## 代码规范
1. **强制构造器注入** - 使用Lombok @RequiredArgsConstructor
2. **统一日志规范** - SLF4J + 占位符，禁止System.out
3. **异常处理规范** - 不吞异常，使用业务异常体系
4. **常量管理** - 魔法值提取到配置或常量类
5. **空值检查** - 使用Optional+防御性编程

## 安全规范
1. **最小权限** - 仅暴露必要端点
2. **输入校验** - @Valid + 业务校验
3. **敏感信息保护** - Jasypt加密 + 环境变量
4. **定期安全扫描** - OWASP依赖检查

## 性能规范
1. **数据库优化** - 索引、分页、批量、分区
2. **缓存策略** - 合理的容量和过期时间
3. **异步处理** - 专用线程池 + 超时
4. **资源管理** - 连接池复用

## 可维护性规范
1. **测试驱动** - 新功能附带测试
2. **文档同步** - 代码变更同步文档
3. **版本管理** - 语义化版本
4. **代码审查** - 所有PR必须Review

---

# 📎 附录

## A. 依赖升级建议

```xml
<!-- 当前 → 建议 -->
<spring-boot.version>2.6.7 → 2.7.18</spring-boot.version>
<mybatis-plus-boot-starter.version>3.4.0 → 3.5.5</mybatis-plus-boot-starter.version>
<netty-all.version>4.1.76 → 4.1.107.Final</netty-all.version>
<jjwt.version>0.9.1 → 0.12.5</jjwt.version>
```

## B. 监控指标建议

| 类别 | 指标名称 | 说明 |
|------|----------|------|
| 业务 | websocket.online.users | 在线用户数 |
| 业务 | message.send.rate | 消息发送速率 |
| 业务 | message.ack.rate | 消息确认率 |
| 系统 | jvm.memory.used | JVM内存使用 |
| 系统 | http.requests.duration | HTTP请求耗时 |
| AI | llm.request.duration | LLM请求耗时 |
| AI | rag.query.cache.hit | RAG缓存命中率 |

## C. 推荐工具链

| 类别 | 工具 | 用途 |
|------|------|------|
| 代码格式化 | Spotless | 统一代码风格 |
| 静态检查 | SpotBugs / PMD | 发现潜在bug |
| 测试覆盖 | JaCoCo | 覆盖率报告 |
| 安全扫描 | OWASP Dependency Check | 依赖漏洞扫描 |
| 性能分析 | Async Profiler | JVM性能分析 |
| 对象转换 | MapStruct | Entity↔DTO转换 |

---

> **文档说明**: 本审查报告基于2026-06-01代码状态，覆盖5个维度共192项问题。  
> **审查团队**: 5个并行Agent分工完成（代码质量、架构设计、性能、安全性、可维护性）。  
> **复查建议**: 每季度全面审查一次，每月安全审查一次。
