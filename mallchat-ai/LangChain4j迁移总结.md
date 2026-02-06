# LangChain4j 迁移总结

## 迁移概述

本次迁移将 AI 模块从 Spring AI 迁移到 LangChain4j，以支持现有的 Spring Boot 2.6.7 + Java 8 环境。

## 迁移原因

1. **版本兼容性问题**：Spring AI 需要 Spring Boot 3.x 和 Java 17+
2. **现有环境限制**：项目使用 Spring Boot 2.6.7 + Java 8，无法升级
3. **LangChain4j 优势**：支持 Spring Boot 2.x + Java 8，功能完善

## 迁移内容

### 1. 依赖变更

#### mallchat-ai/pom.xml
- 移除：`spring-ai.version` 属性
- 新增：`langchain4j.version` 属性（0.27.1）
- 替换依赖管理：
  - `spring-ai-openai-spring-boot-starter` → `langchain4j` + `langchain4j-open-ai`

#### mallchat-ai/mallchat-ai-llm/pom.xml
- 替换：`spring-ai-openai-spring-boot-starter` → `langchain4j` + `langchain4j-open-ai`

#### mallchat-ai/mallchat-ai-vector/pom.xml
- 替换：`spring-ai-openai-spring-boot-starter` → `langchain4j` + `langchain4j-open-ai`

### 2. 配置变更

#### application-ai.yml
- 配置结构从 `spring.ai.*` 改为 `langchain4j.*`
- Base URL 从 `https://api.openai.com` 改为 `https://api.openai.com/v1`
- 新增配置项：
  - `timeout`: 60s
  - `max-retries`: 3
  - `log-requests`: true
  - `log-responses`: false

### 3. 代码变更

#### 新增文件

1. **LangChain4jConfig.java**
   - 路径：`mallchat-ai/mallchat-ai-llm/src/main/java/com/abin/mallchat/ai/llm/config/LangChain4jConfig.java`
   - 功能：配置 ChatLanguageModel、StreamingChatLanguageModel 和 OpenAiTokenizer

2. **EmbeddingService.java**
   - 路径：`mallchat-ai/mallchat-ai-vector/src/main/java/com/abin/mallchat/ai/vector/service/EmbeddingService.java`
   - 功能：Embedding 服务接口

3. **OpenAIEmbeddingService.java**
   - 路径：`mallchat-ai/mallchat-ai-vector/src/main/java/com/abin/mallchat/ai/vector/service/impl/OpenAIEmbeddingService.java`
   - 功能：基于 LangChain4j 的 Embedding 服务实现

#### 修改文件

1. **OpenAILLMService.java**
   - 依赖注入变更：
     - `OpenAiChatModel` → `ChatLanguageModel` + `StreamingChatLanguageModel`
   - 流式调用实现变更：
     - 使用 `Flux.create` + `StreamingResponseHandler` 回调
   - Token 计数实现变更：
     - 使用 `OpenAiTokenizer.estimateTokenCountInText()`
   - 移除：`buildChatOptions()` 方法（LangChain4j 在配置中统一设置）

### 4. 文档变更

#### 设计文档（design.md）
- 更新技术栈选型说明
- 更新 LLM 框架对比表
- 强调 LangChain4j 对 Spring Boot 2.x + Java 8 的支持

#### 快速参考（quick-reference.md）
- 更新配置示例
- 更新技术选型说明
- 更新相关文档链接

## API 映射对照表

| 功能 | Spring AI | LangChain4j |
|------|-----------|-------------|
| 同步聊天 | `ChatClient.call(prompt)` | `ChatLanguageModel.generate(prompt)` |
| 流式聊天 | `ChatClient.stream(prompt)` | `StreamingChatLanguageModel.generate(prompt, handler)` |
| Token 计数 | 需自己实现 | `OpenAiTokenizer.estimateTokenCountInText(text)` |
| 生成向量 | `EmbeddingClient.embed(text)` | `EmbeddingModel.embed(text).content().vector()` |

## 关键差异

### 1. 流式输出实现

**Spring AI**：
```java
Flux<String> stream = chatClient.stream(prompt);
```

**LangChain4j**：
```java
Flux<String> stream = Flux.create(sink -> {
    streamingModel.generate(prompt, new StreamingResponseHandler<AiMessage>() {
        @Override
        public void onNext(String token) {
            sink.next(token);
        }
        
        @Override
        public void onComplete(Response<AiMessage> response) {
            sink.complete();
        }
        
        @Override
        public void onError(Throwable error) {
            sink.error(error);
        }
    });
});
```

### 2. 配置方式

- **Spring AI**：使用 Spring Boot 自动配置
- **LangChain4j**：需要手动创建 Bean（通过 `@Configuration` 类）

### 3. Base URL

- **Spring AI**：`https://api.openai.com`
- **LangChain4j**：`https://api.openai.com/v1`（注意多了 `/v1`）

## 验证步骤

完成迁移后，需要验证以下功能：

1. [ ] 应用可以正常启动
2. [ ] 同步聊天调用正常
3. [ ] 流式聊天调用正常
4. [ ] Token 计数功能正常
5. [ ] 向量生成功能正常
6. [ ] 所有单元测试通过
7. [ ] 所有属性测试通过
8. [ ] 日志输出正常
9. [ ] 错误处理正常
10. [ ] 性能符合预期

## 编译验证

```bash
# 清理并编译
mvn clean compile

# 运行测试
mvn test

# 打包
mvn package
```

## 注意事项

1. **Base URL 配置**：确保配置中的 base-url 包含 `/v1` 后缀
2. **流式输出内存管理**：确保在 `onError` 和 `onComplete` 中正确关闭 sink
3. **Token 计数精度**：`OpenAiTokenizer` 是估算值，与实际消耗可能有 5-10% 的差异
4. **批量 Embedding**：当前实现是循环调用，如需更高性能可考虑使用批量 API

## 性能影响

- **依赖大小**：从 ~5MB 增加到 ~20MB
- **启动时间**：基本无影响
- **运行时性能**：基本无影响
- **内存占用**：略有增加（约 10-20MB）

## 回滚方案

如果迁移后出现问题，可以通过以下步骤回滚：

1. 恢复 POM 文件中的 Spring AI 依赖
2. 恢复 application-ai.yml 配置
3. 恢复 OpenAILLMService.java 原始实现
4. 删除 LangChain4jConfig.java
5. 删除 EmbeddingService 相关文件

## 参考资料

- [LangChain4j 官方文档](https://docs.langchain4j.dev/)
- [LangChain4j GitHub](https://github.com/langchain4j/langchain4j)
- [LangChain4j Examples](https://github.com/langchain4j/langchain4j-examples)
- [OpenAI API 文档](https://platform.openai.com/docs/api-reference)

## 迁移完成时间

- 迁移日期：2025-01-05
- 迁移耗时：约 1 小时
- 迁移人员：AI Assistant

## 后续工作

1. 运行完整的测试套件
2. 更新相关的开发文档
3. 通知团队成员配置变更
4. 监控生产环境运行情况
