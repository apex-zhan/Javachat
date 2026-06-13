# AI 模块迁移文档总览

## 📚 文档导航

本目录包含了从 Spring AI 迁移到 LangChain4j，并进一步升级到 **Ollama + Qdrant** 本地开源方案的完整文档。

---

## 🎯 推荐阅读顺序

### 1. 决策阶段

首先阅读这些文档，了解为什么选择 LangChain4j 和本地开源方案：

1. **[框架对比分析](LLM%20框架对比：Spring%20AI%20vs%20LangChain4j.md)** ⭐⭐⭐⭐⭐
   - Spring AI vs LangChain4j vs 直接调用 API
   - 详细的功能对比和适用场景
   - 帮助你做出正确的技术选型

2. **[Spring AI 升级指南](Spring%20AI%20升级指南.md)** ⭐⭐⭐
   - 如果选择升级到 Spring Boot 3.x
   - 包含完整的升级步骤和注意事项
   - 备选方案参考

3. **[AI技术方案](../AI技术方案.md)** ⭐⭐⭐⭐⭐
   - 当前完整技术架构
   - Ollama + Qdrant + bge + Qwen 方案详解
   - 部署和配置参考

### 2. 执行阶段

决定使用 LangChain4j 后，按顺序阅读：

4. **[迁移方案](LangChain4j%20迁移方案.md)** ⭐⭐⭐⭐⭐
   - 完整的迁移步骤（两个阶段）
   - 每个阶段的详细说明
   - 代码示例和配置
   - **必读文档**

5. **[迁移总结](LangChain4j迁移总结.md)** ⭐⭐⭐⭐⭐
   - 迁移决策和理由
   - 详细的变更记录
   - API 映射表
   - 问题排查指南
   - **必读文档**

6. **[代码重构详细指南](../../../docs/代码重构详细指南.md)** ⭐⭐⭐⭐⭐
   - 每个文件的详细变更说明
   - 变更前后的代码对比
   - API 映射表
   - **必读文档**

### 3. 参考阶段

迁移完成后，随时查阅：

7. **[LangChain4j 快速参考](LangChain4j%20快速参考卡.md)** ⭐⭐⭐⭐⭐
   - 常用 API 速查
   - 配置选项参考
   - 常用模式和最佳实践
   - **日常开发必备**

8. **[Mock模式使用指南](../Mock模式使用指南.md)** ⭐⭐⭐⭐⭐
   - Mock 模式启动和配置
   - 本地开发无需外部依赖
   - **日常开发必备**

9. **[Embedding模型配置指南](../../mallchat-ai-vector/docs/Embedding模型配置指南.md)** ⭐⭐⭐⭐
   - bge / m3e / OpenAI 配置
   - Ollama 部署指南
   - 向量库配置

---

## 📋 迁移检查清单

### 准备工作
- [x] 阅读框架对比分析
- [x] 确认使用 LangChain4j + Ollama + Qdrant
- [x] 备份现有代码
- [ ] 准备 Ollama 环境
- [ ] 准备 Qdrant 环境
- [ ] 准备 OpenAI API Key（兼容备用）
- [ ] 检查网络连接

### 依赖调整
- [x] 更新 `mallchat-ai/pom.xml`
- [x] 更新 `mallchat-ai-llm/pom.xml`
- [x] 更新 `mallchat-ai-vector/pom.xml`
- [x] 更新 `mallchat-ai-rag/pom.xml`

### 配置调整
- [x] 更新 `application-ai.yml`
- [x] 新增 `application-local.yml`
- [x] 新增 `application-mock.yml`
- [ ] 设置环境变量 `OLLAMA_BASE_URL`
- [ ] 设置环境变量 `QDRANT_HOST`
- [ ] 设置环境变量 `QDRANT_PORT`

### 代码调整
- [x] 创建 `LangChain4jConfig.java`
- [x] 创建 `QwenLLMService.java`
- [x] 创建 `LlamaLLMService.java`
- [x] 创建 `LLMServiceFactory.java`
- [x] 创建 `LLMProvider.java`
- [x] 创建 `OllamaBgeEmbeddingService.java`
- [x] 创建 `M3eEmbeddingService.java`
- [x] 创建 `QdrantVectorService.java`
- [x] 创建 `MockLLMService.java`
- [x] 创建 `MockEmbeddingService.java`
- [x] 创建 `MockVectorService.java`
- [x] 修改 `OpenAILLMService.java`
- [x] 修改 `RAGServiceImpl.java`
- [x] 修改 `AIAssistantServiceImpl.java`
- [x] 更新单元测试

### 验证测试
- [x] 编译成功 (`mvn clean compile`)
- [x] 单元测试通过 (`mvn test`)
- [x] Mock 模式启动成功
- [x] 同步调用测试通过
- [x] 流式调用测试通过
- [ ] 向量生成测试通过（需要 Ollama）
- [ ] 向量检索测试通过（需要 Qdrant）
- [ ] 本地模式启动成功

### 完成工作
- [x] 更新项目文档
- [ ] 提交代码变更
- [ ] 部署到测试环境
- [ ] 监控运行状态

---

## 🚀 快速开始

### 最快路径（3 步）

1. **阅读迁移方案**（15 分钟）
2. **执行迁移步骤**（3 小时）
3. **Mock 模式验证**（5 分钟）

```bash
# 1. 编译
mvn clean compile

# 2. 运行测试
mvn test

# 3. Mock 模式启动
mvn spring-boot:run -pl mallchat-chat-server -Dspring-boot.run.profiles=mock

# 4. 测试 AI 助手接口
curl -X POST http://localhost:8080/api/ai/assistant/question \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{"userId": 10001, "question": "你好"}'

# 5. 测试 RAG 接口
curl -X POST http://localhost:8080/api/stream/rag/query \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{"userId": 10001, "question": "MallChat 是什么？"}'
```

---

## 📊 文档概览

| 文档 | 阅读时间 | 重要性 | 适用阶段 |
|------|---------|--------|---------|
| 框架对比分析 | 20 分钟 | ⭐⭐⭐⭐⭐ | 决策 |
| Spring AI 升级指南 | 15 分钟 | ⭐⭐⭐ | 决策 |
| AI技术方案 | 30 分钟 | ⭐⭐⭐⭐⭐ | 决策/执行 |
| 迁移方案 | 30 分钟 | ⭐⭐⭐⭐⭐ | 执行 |
| 迁移总结 | 20 分钟 | ⭐⭐⭐⭐⭐ | 执行 |
| 代码重构指南 | 25 分钟 | ⭐⭐⭐⭐⭐ | 执行 |
| 快速参考 | 10 分钟 | ⭐⭐⭐⭐⭐ | 参考 |
| Mock模式使用指南 | 10 分钟 | ⭐⭐⭐⭐⭐ | 参考 |
| Embedding模型配置指南 | 15 分钟 | ⭐⭐⭐⭐ | 参考 |

---

## 🎯 核心要点

### 为什么选择 LangChain4j + Ollama + Qdrant？

1. ✅ **Spring Boot 2.x 兼容** - 无需升级 Spring Boot
2. ✅ **本地部署** - 数据不出域，无 API 费用
3. ✅ **动态向量** - Qdrant 支持 1024/768 维共存
4. ✅ **Mock 模式** - 无外部依赖即可启动
5. ✅ **多模型支持** - Qwen、Llama、OpenAI、ChatGLM 自由切换
6. ✅ **社区活跃** - 文档完善，问题响应快

### 迁移工作量

- **总时间**：3-5 天
- **修改文件**：10+
- **新增文件**：10+
- **风险等级**：中
- **回滚难度**：中等

### 关键变化

| 方面 | Spring AI | LangChain4j 0.36 |
|------|-----------|------------------|
| 依赖 | `spring-ai-openai-spring-boot-starter` | `langchain4j` + `langchain4j-ollama` |
| 配置 | `spring.ai.*` | `langchain4j.*` / `embedding.*` / `vector.*` |
| 同步调用 | `ChatClient.call()` | `ChatLanguageModel.generate()` |
| 流式调用 | `ChatClient.stream()` | `StreamingChatLanguageModel.generate()` |
| 向量生成 | `EmbeddingClient.embed()` | `EmbeddingModel.embed()` |
| LLM 部署 | OpenAI API | Ollama 本地 |
| 向量库 | Milvus | Qdrant |

---

## 🆘 获取帮助

### 遇到问题？

1. **查阅文档**
   - 先查看迁移总结中的"问题排查"部分
   - 查看代码重构指南中的"常见陷阱"
   - 查看 [Mock模式使用指南](../Mock模式使用指南.md)

2. **搜索 Issues**
   - [LangChain4j GitHub Issues](https://github.com/langchain4j/langchain4j/issues)
   - [Ollama GitHub Issues](https://github.com/ollama/ollama/issues)
   - [Qdrant GitHub Issues](https://github.com/qdrant/qdrant/issues)

3. **查看示例**
   - [LangChain4j Examples](https://github.com/langchain4j/langchain4j-examples)
   - 找到类似的使用场景

4. **阅读官方文档**
   - [LangChain4j 官方文档](https://docs.langchain4j.dev/)
   - [Ollama 官方文档](https://ollama.com/)
   - [Qdrant 官方文档](https://qdrant.tech/documentation/)

### 常见问题快速链接

- [Mock 模式启动失败](../Mock模式使用指南.md#q1-启动时报错-beandefinitionoverrideexception)
- [Ollama 无法连接](../Mock模式使用指南.md#q3-ollama-服务无法连接)
- [向量维度不匹配](../../mallchat-ai-vector/docs/Embedding模型配置指南.md#q2-向量维度不匹配怎么办)
- [依赖下载失败](LangChain4j%20迁移方案.md#q1-依赖下载失败怎么办)
- [流式输出不工作](LangChain4j%20迁移方案.md#q4-流式输出不工作怎么办)

---

## 📈 迁移进度跟踪

### 阶段 1：准备（已完成）
- [x] 阅读文档
- [x] 备份代码
- [x] 技术选型

### 阶段 2：依赖调整（已完成）
- [x] 更新 POM 文件
- [x] 下载依赖

### 阶段 3：配置调整（已完成）
- [x] 更新配置文件
- [x] 新增 Mock 配置

### 阶段 4：代码重构（已完成）
- [x] 创建配置类
- [x] 创建 Ollama 服务实现
- [x] 创建 Qdrant 向量服务
- [x] 创建 Mock 系列实现
- [x] 重构服务实现
- [x] 更新测试代码

### 阶段 5：验证测试（部分完成）
- [x] 编译验证
- [x] 单元测试
- [x] Mock 模式集成测试
- [ ] Ollama 本地测试
- [ ] Qdrant 向量检索测试
- [ ] 生产环境测试

---

## 🎉 迁移完成后

### 验证清单
- [x] 所有测试通过
- [x] 应用正常启动（Mock 模式）
- [x] LLM 调用正常（Mock 模式）
- [x] 流式输出正常
- [x] Embedding 生成正常（Mock 模式）
- [ ] 向量生成正常（Ollama）
- [ ] 向量检索正常（Qdrant）
- [ ] 性能符合预期

### 后续工作
1. 部署 Ollama 和 Qdrant
2. 本地模式验证
3. 更新团队文档
4. 培训团队成员
5. 监控生产环境
6. 收集用户反馈
7. 持续优化

---

## 📞 联系方式

- **项目负责人**：AI Assistant
- **技术支持**：查阅 LangChain4j / Ollama / Qdrant 官方文档
- **问题反馈**：GitHub Issues

---

## 📝 版本历史

### v2.0 (2026-06-13)
- ✅ 升级到 LangChain4j 0.36.0
- ✅ 接入 Ollama + Qdrant 本地开源方案
- ✅ 增加 Mock 模式
- ✅ 增加多模型支持
- ✅ 更新所有相关文档

### v1.0 (2025-01-05)
- ✅ 完成从 Spring AI 到 LangChain4j 的迁移方案设计
- ✅ 创建完整文档集
- ✅ 提供代码示例
- ✅ 编写快速参考

### 待办事项
- [ ] 生产环境 Ollama 部署验证
- [ ] 生产环境 Qdrant 部署验证
- [ ] 性能基准测试
- [ ] RAG 效果评估
- [ ] 收集反馈

---

## 🌟 推荐资源

### 官方资源
- [LangChain4j 官方文档](https://docs.langchain4j.dev/)
- [LangChain4j GitHub](https://github.com/langchain4j/langchain4j)
- [LangChain4j Examples](https://github.com/langchain4j/langchain4j-examples)
- [Ollama 官方文档](https://ollama.com/)
- [Qdrant 官方文档](https://qdrant.tech/documentation/)

### 相关技术
- [OpenAI API 文档](https://platform.openai.com/docs/api-reference)
- [Milvus 文档](https://milvus.io/docs)
- [Spring Boot 文档](https://spring.io/projects/spring-boot)

---

**祝你使用顺利！** 🚀

如有任何问题，请查阅相关文档或联系技术支持。
