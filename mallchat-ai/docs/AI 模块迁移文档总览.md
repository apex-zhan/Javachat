# AI 模块迁移文档总览

## 📚 文档导航

本目录包含了从 Spring AI 迁移到 LangChain4j 的完整文档。

---

## 🎯 推荐阅读顺序

### 1. 决策阶段
首先阅读这些文档，了解为什么选择 LangChain4j：

1. **[框架对比分析](LLM%20框架对比：Spring%20AI%20vs%20LangChain4j.md)** ⭐⭐⭐⭐⭐
   - Spring AI vs LangChain4j vs 直接调用 API
   - 详细的功能对比和适用场景
   - 帮助你做出正确的技术选型

2. **[Spring AI 升级指南](Spring%20AI%20升级指南.md)** ⭐⭐⭐
   - 如果选择升级到 Spring Boot 3.x
   - 包含完整的升级步骤和注意事项
   - 备选方案参考

### 2. 执行阶段
决定使用 LangChain4j 后，按顺序阅读：

3. **[迁移方案](LangChain4j%20迁移方案.md)** ⭐⭐⭐⭐⭐
   - 完整的迁移步骤（5 个阶段）
   - 每个阶段的详细说明
   - 预计工作量和时间估算
   - **必读文档**

4. **[代码重构指南](代码重构详细指南.md)** ⭐⭐⭐⭐⭐
   - 每个文件的详细变更说明
   - 变更前后的代码对比
   - API 映射表
   - **必读文档**

5. **[迁移总结](迁移总结文档.md)** ⭐⭐⭐⭐
   - 迁移决策和理由
   - 详细的变更记录
   - 性能基准测试
   - 问题排查指南

### 3. 参考阶段
迁移完成后，随时查阅：

6. **[LangChain4j 快速参考](LangChain4j%20快速参考卡.md)** ⭐⭐⭐⭐⭐
   - 常用 API 速查
   - 配置选项参考
   - 常用模式和最佳实践
   - **日常开发必备**

---

## 📋 迁移检查清单

### 准备工作
- [ ] 阅读框架对比分析
- [ ] 确认使用 LangChain4j
- [ ] 备份现有代码
- [ ] 准备 OpenAI API Key
- [ ] 检查网络连接

### 依赖调整
- [ ] 更新 `mallchat-ai/pom.xml`
- [ ] 更新 `mallchat-ai-llm/pom.xml`
- [ ] 更新 `mallchat-ai-vector/pom.xml`
- [ ] 更新 `mallchat-ai-rag/pom.xml`

### 配置调整
- [ ] 更新 `application-ai.yml`
- [ ] 设置环境变量 `OPENAI_API_KEY`

### 代码调整
- [ ] 创建 `LangChain4jConfig.java`
- [ ] 重构 `OpenAILLMService.java`
- [ ] 创建 `EmbeddingService.java`
- [ ] 创建 `OpenAIEmbeddingService.java`
- [ ] 更新 `OpenAILLMServiceTest.java`

### 验证测试
- [ ] 编译成功 (`mvn clean compile`)
- [ ] 单元测试通过 (`mvn test`)
- [ ] 启动应用成功
- [ ] 同步调用测试通过
- [ ] 流式调用测试通过
- [ ] 向量生成测试通过

### 完成工作
- [ ] 更新项目文档
- [ ] 提交代码变更
- [ ] 部署到测试环境
- [ ] 监控运行状态

---

## 🚀 快速开始

### 最快路径（3 步）

如果你想快速开始，只需：

1. **阅读迁移方案**（15 分钟）
   ```bash
   cat docs/LangChain4j 迁移方案.md
   ```

2. **执行迁移步骤**（3 小时）
   - 按照迁移方案的 5 个阶段执行
   - 遇到问题查阅代码重构指南

3. **验证功能**（30 分钟）
   ```bash
   mvn clean test
   mvn spring-boot:run
   ```

---

## 📊 文档概览

| 文档 | 页数 | 阅读时间 | 重要性 | 适用阶段 |
|------|------|---------|--------|---------|
| 框架对比分析 | 8 | 20 分钟 | ⭐⭐⭐⭐⭐ | 决策 |
| Spring AI 升级指南 | 6 | 15 分钟 | ⭐⭐⭐ | 决策 |
| 迁移方案 | 12 | 30 分钟 | ⭐⭐⭐⭐⭐ | 执行 |
| 代码重构指南 | 10 | 25 分钟 | ⭐⭐⭐⭐⭐ | 执行 |
| 迁移总结 | 8 | 20 分钟 | ⭐⭐⭐⭐ | 执行 |
| 快速参考 | 6 | 10 分钟 | ⭐⭐⭐⭐⭐ | 参考 |

---

## 🎯 核心要点

### 为什么选择 LangChain4j？

1. ✅ **零升级成本** - 支持 Spring Boot 2.6.7 + Java 8
2. ✅ **功能更强** - RAG 能力比 Spring AI 更完善
3. ✅ **稳定可靠** - 依赖可以正常拉取
4. ✅ **快速上线** - 无需大规模重构
5. ✅ **社区活跃** - 文档完善，问题响应快

### 迁移工作量

- **总时间**：3-4 小时
- **修改文件**：8 个
- **新增文件**：2 个
- **风险等级**：低
- **回滚难度**：容易

### 关键变化

| 方面 | Spring AI | LangChain4j |
|------|-----------|-------------|
| 依赖 | `spring-ai-openai-spring-boot-starter` | `langchain4j-open-ai` |
| 配置 | `spring.ai.*` | `langchain4j.*` |
| 同步调用 | `ChatClient.call()` | `ChatLanguageModel.generate()` |
| 流式调用 | `ChatClient.stream()` | `StreamingChatLanguageModel.generate()` |
| 向量生成 | `EmbeddingClient.embed()` | `EmbeddingModel.embed()` |

---

## 🆘 获取帮助

### 遇到问题？

1. **查阅文档**
   - 先查看迁移总结中的"问题排查"部分
   - 查看代码重构指南中的"常见陷阱"

2. **搜索 Issues**
   - [LangChain4j GitHub Issues](https://github.com/langchain4j/langchain4j/issues)
   - 搜索关键词：你遇到的错误信息

3. **查看示例**
   - [LangChain4j Examples](https://github.com/langchain4j/langchain4j-examples)
   - 找到类似的使用场景

4. **阅读官方文档**
   - [LangChain4j 官方文档](https://docs.langchain4j.dev/)
   - 查找具体 API 的使用方法

### 常见问题快速链接

- [依赖下载失败](迁移总结文档.md#q1-依赖下载失败)
- [API Key 无效](迁移总结文档.md#q2-api-key-无效)
- [流式输出不工作](迁移总结文档.md#q3-流式输出不工作)
- [Token 计数不准确](迁移总结文档.md#q4-token-计数不准确)
- [编译错误](迁移总结文档.md#q5-编译错误)

---

## 📈 迁移进度跟踪

### 阶段 1：准备（预计 30 分钟）
- [ ] 阅读文档
- [ ] 备份代码
- [ ] 准备环境

### 阶段 2：依赖调整（预计 30 分钟）
- [ ] 更新 POM 文件
- [ ] 下载依赖

### 阶段 3：配置调整（预计 15 分钟）
- [ ] 更新配置文件
- [ ] 设置环境变量

### 阶段 4：代码重构（预计 2-3 小时）
- [ ] 创建配置类
- [ ] 重构服务实现
- [ ] 更新测试代码

### 阶段 5：验证测试（预计 1 小时）
- [ ] 编译验证
- [ ] 单元测试
- [ ] 集成测试

---

## 🎉 迁移完成后

### 验证清单
- [ ] 所有测试通过
- [ ] 应用正常启动
- [ ] LLM 调用正常
- [ ] 流式输出正常
- [ ] 向量生成正常
- [ ] 性能符合预期

### 后续工作
1. 更新团队文档
2. 培训团队成员
3. 监控生产环境
4. 收集用户反馈
5. 持续优化

---

## 📞 联系方式

- **项目负责人**：AI Assistant
- **技术支持**：查阅 LangChain4j 官方文档
- **问题反馈**：GitHub Issues

---

## 📝 版本历史

### v1.0 (2025-01-05)
- ✅ 完成迁移方案设计
- ✅ 创建完整文档集
- ✅ 提供代码示例
- ✅ 编写快速参考

### 待办事项
- [ ] 执行迁移
- [ ] 验证功能
- [ ] 部署测试
- [ ] 收集反馈

---

## 🌟 推荐资源

### 官方资源
- [LangChain4j 官方文档](https://docs.langchain4j.dev/)
- [LangChain4j GitHub](https://github.com/langchain4j/langchain4j)
- [LangChain4j Examples](https://github.com/langchain4j/langchain4j-examples)

### 社区资源
- [LangChain4j Discord](https://discord.gg/langchain4j)
- [Stack Overflow](https://stackoverflow.com/questions/tagged/langchain4j)

### 相关技术
- [OpenAI API 文档](https://platform.openai.com/docs/api-reference)
- [Milvus 文档](https://milvus.io/docs)
- [Spring Boot 文档](https://spring.io/projects/spring-boot)

---

**祝你迁移顺利！** 🚀

如有任何问题，请查阅相关文档或联系技术支持。
