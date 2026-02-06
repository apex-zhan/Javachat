# LangChain4j 快速参考卡

## 🚀 快速开始

### 1. 添加依赖
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
    <version>0.27.1</version>
</dependency>
```

### 2. 配置 API Key
```yaml
langchain4j:
  openai:
    api-key: ${OPENAI_API_KEY}
```

### 3. 创建配置类
```java
@Configuration
public class LangChain4jConfig {
    @Bean
    public ChatLanguageModel chatModel() {
        return OpenAiChatModel.builder()
            .apiKey(apiKey)
            .build();
    }
}
```

### 4. 使用服务
```java
@Service
public class AIService {
    @Autowired
    private ChatLanguageModel chatModel;
    
    public String chat(String prompt) {
        return chatModel.generate(prompt);
    }
}
```

---

## 📖 核心 API

### 同步聊天
```java
ChatLanguageModel model = OpenAiChatModel.builder()
    .apiKey("sk-...")
    .modelName("gpt-3.5-turbo")
    .temperature(0.7)
    .build();

String response = model.generate("你好");
```

### 流式聊天
```java
StreamingChatLanguageModel model = OpenAiStreamingChatModel.builder()
    .apiKey("sk-...")
    .build();

model.generate("写一篇文章", new StreamingResponseHandler<AiMessage>() {
    @Override
    public void onNext(String token) {
        System.out.print(token);
    }
    
    @Override
    public void onComplete(Response<AiMessage> response) {
        System.out.println("\n完成");
    }
    
    @Override
    public void onError(Throwable error) {
        error.printStackTrace();
    }
});
```

### 生成向量
```java
EmbeddingModel model = OpenAiEmbeddingModel.builder()
    .apiKey("sk-...")
    .modelName("text-embedding-ada-002")
    .build();

Response<Embedding> response = model.embed("这是一段文本");
float[] vector = response.content().vector();
```

### Token 计数
```java
OpenAiTokenizer tokenizer = new OpenAiTokenizer("gpt-3.5-turbo");
int count = tokenizer.estimateTokenCountInText("Hello, world!");
```

---

## 🔧 配置选项

### ChatLanguageModel 配置
```java
OpenAiChatModel.builder()
    .apiKey("sk-...")                    // API Key（必需）
    .baseUrl("https://api.openai.com/v1") // Base URL
    .modelName("gpt-3.5-turbo")          // 模型名称
    .temperature(0.7)                     // 温度（0-2）
    .topP(1.0)                           // Top P
    .maxTokens(2000)                     // 最大 Token 数
    .frequencyPenalty(0.0)               // 频率惩罚
    .presencePenalty(0.0)                // 存在惩罚
    .timeout(Duration.ofSeconds(60))     // 超时时间
    .maxRetries(3)                       // 最大重试次数
    .logRequests(true)                   // 记录请求
    .logResponses(false)                 // 记录响应
    .build();
```

### EmbeddingModel 配置
```java
OpenAiEmbeddingModel.builder()
    .apiKey("sk-...")
    .modelName("text-embedding-ada-002")
    .timeout(Duration.ofSeconds(30))
    .maxRetries(3)
    .build();
```

---

## 🎨 常用模式

### 1. 带历史的对话
```java
List<ChatMessage> messages = new ArrayList<>();
messages.add(new UserMessage("你好"));
messages.add(new AiMessage("你好！有什么可以帮助你的？"));
messages.add(new UserMessage("我刚才说了什么？"));

String response = chatModel.generate(messages);
```

### 2. 系统提示词
```java
List<ChatMessage> messages = List.of(
    new SystemMessage("你是一个专业的 Java 开发者"),
    new UserMessage("如何使用 Spring Boot？")
);

String response = chatModel.generate(messages);
```

### 3. 函数调用
```java
@Tool("获取天气信息")
public String getWeather(String location) {
    return "北京今天晴天，25度";
}

// LangChain4j 会自动识别并调用函数
```

### 4. 流式输出到 Flux
```java
public Flux<String> streamChat(String prompt) {
    return Flux.create(sink -> {
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
}
```

### 5. 批量生成向量
```java
List<String> texts = List.of("文本1", "文本2", "文本3");
List<float[]> vectors = texts.stream()
    .map(text -> embeddingModel.embed(text).content().vector())
    .collect(Collectors.toList());
```

---

## 🌐 支持的模型

### OpenAI 聊天模型
- `gpt-4`
- `gpt-4-turbo-preview`
- `gpt-3.5-turbo`
- `gpt-3.5-turbo-16k`

### OpenAI 嵌入模型
- `text-embedding-ada-002`
- `text-embedding-3-small`
- `text-embedding-3-large`

### 其他提供商
- Azure OpenAI
- Anthropic Claude
- Google Vertex AI
- Hugging Face
- Ollama (本地)
- 更多...

---

## ⚙️ Spring Boot 集成

### application.yml 配置
```yaml
langchain4j:
  openai:
    api-key: ${OPENAI_API_KEY}
    base-url: https://api.openai.com/v1
    timeout: 60s
    max-retries: 3
    
    chat-model:
      model-name: gpt-3.5-turbo
      temperature: 0.7
      max-tokens: 2000
    
    streaming-chat-model:
      model-name: gpt-3.5-turbo
      temperature: 0.7
    
    embedding-model:
      model-name: text-embedding-ada-002
```

### 配置类
```java
@Configuration
public class LangChain4jConfig {
    
    @Value("${langchain4j.openai.api-key}")
    private String apiKey;
    
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
            .apiKey(apiKey)
            .build();
    }
    
    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel() {
        return OpenAiStreamingChatModel.builder()
            .apiKey(apiKey)
            .build();
    }
    
    @Bean
    public EmbeddingModel embeddingModel() {
        return OpenAiEmbeddingModel.builder()
            .apiKey(apiKey)
            .build();
    }
}
```

---

## 🐛 错误处理

### 捕获异常
```java
try {
    String response = chatModel.generate(prompt);
} catch (RuntimeException e) {
    if (e.getMessage().contains("401")) {
        // API Key 无效
    } else if (e.getMessage().contains("429")) {
        // 速率限制
    } else if (e.getMessage().contains("timeout")) {
        // 超时
    }
}
```

### 重试配置
```java
OpenAiChatModel.builder()
    .maxRetries(3)  // 自动重试 3 次
    .build();
```

### 流式错误处理
```java
new StreamingResponseHandler<AiMessage>() {
    @Override
    public void onError(Throwable error) {
        log.error("流式调用失败", error);
        // 处理错误
    }
}
```

---

## 📊 性能优化

### 1. 连接池
```java
// LangChain4j 内部使用 OkHttp，自动管理连接池
```

### 2. 超时设置
```java
.timeout(Duration.ofSeconds(60))  // 根据实际情况调整
```

### 3. 批量处理
```java
// 批量生成向量时，使用并行流
List<float[]> vectors = texts.parallelStream()
    .map(text -> embeddingModel.embed(text).content().vector())
    .collect(Collectors.toList());
```

### 4. 缓存
```java
@Cacheable("embeddings")
public float[] generateEmbedding(String text) {
    return embeddingModel.embed(text).content().vector();
}
```

---

## 🔍 调试技巧

### 启用请求日志
```java
.logRequests(true)
.logResponses(true)
```

### 日志级别
```yaml
logging:
  level:
    dev.langchain4j: DEBUG
```

### 查看 Token 使用
```java
Response<AiMessage> response = chatModel.generate(prompt);
TokenUsage usage = response.tokenUsage();
System.out.println("输入 tokens: " + usage.inputTokenCount());
System.out.println("输出 tokens: " + usage.outputTokenCount());
System.out.println("总计 tokens: " + usage.totalTokenCount());
```

---

## 📚 常用链接

- [官方文档](https://docs.langchain4j.dev/)
- [GitHub](https://github.com/langchain4j/langchain4j)
- [示例代码](https://github.com/langchain4j/langchain4j-examples)
- [API 文档](https://docs.langchain4j.dev/apidocs/)

---

## 💡 最佳实践

1. **API Key 安全**：使用环境变量，不要硬编码
2. **错误处理**：始终处理异常和流式错误
3. **超时设置**：根据实际情况设置合理的超时时间
4. **日志记录**：生产环境关闭响应日志（避免泄露敏感信息）
5. **Token 管理**：监控 Token 使用，避免超出配额
6. **缓存策略**：对相同输入使用缓存
7. **批量处理**：尽可能批量调用以提高效率
8. **资源释放**：确保流式调用正确关闭

---

**版本**：LangChain4j 0.27.1  
**更新日期**：2025-01-05
