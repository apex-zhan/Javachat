# Spring AI 升级指南

## 方案 1：升级 Spring Boot 到 3.x（推荐）

### 升级步骤

#### 1. 升级 Java 版本
```xml
<!-- pom.xml -->
<properties>
    <java.version>17</java.version>  <!-- 从 1.8 升级到 17 -->
</properties>
```

#### 2. 升级 Spring Boot 版本
```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.0</version>  <!-- 从 2.6.7 升级到 3.2.0 -->
    <relativePath/>
</parent>
```

#### 3. 使用 Spring AI BOM 管理版本
```xml
<dependencyManagement>
    <dependencies>
        <!-- Spring AI BOM -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>1.0.0-M4</version>  <!-- 使用稳定的 Milestone 版本 -->
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- 不需要指定版本，由 BOM 管理 -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
    </dependency>
</dependencies>
```

#### 4. 主要依赖升级

| 依赖 | 旧版本 | 新版本 |
|------|--------|--------|
| MyBatis-Plus | 3.4.0 | 3.5.5+ |
| Redisson | 3.17.1 | 3.25.0+ |
| Netty | 4.1.76 | 4.1.100+ |
| RocketMQ | 2.2.2 | 2.3.0+ |

#### 5. 代码迁移要点

**包名变更**：
```java
// 旧版本 (Spring Boot 2.x)
import javax.servlet.*;
import javax.persistence.*;

// 新版本 (Spring Boot 3.x)
import jakarta.servlet.*;
import jakarta.persistence.*;
```

**配置变更**：
```yaml
# 旧版本
spring:
  mvc:
    pathmatch:
      matching-strategy: ANT_PATH_MATCHER

# 新版本（默认就是 PATH_PATTERN_PARSER，无需配置）
```

### 优点
✅ 使用最新的 Spring AI 功能
✅ 更好的性能和安全性
✅ 长期支持和社区活跃

### 缺点
⚠️ 需要升级 Java 到 17
⚠️ 需要迁移代码（javax → jakarta）
⚠️ 可能需要升级其他依赖

---

## 方案 2：使用兼容 Spring Boot 2.x 的替代方案（不推荐）

### 选项 A：直接调用 OpenAI API

不使用 Spring AI，直接调用 OpenAI API：

```xml
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>okhttp</artifactId>
    <version>4.12.0</version>
</dependency>
```

```java
public class OpenAIClient {
    private final OkHttpClient client = new OkHttpClient();
    private final String apiKey;
    
    public Flux<String> streamChat(String prompt) {
        return Flux.create(sink -> {
            Request request = new Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(
                    MediaType.parse("application/json"),
                    buildRequestBody(prompt, true)
                ))
                .build();
                
            try (Response response = client.newCall(request).execute()) {
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body().byteStream())
                );
                
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6);
                        if (!"[DONE]".equals(data)) {
                            sink.next(parseStreamData(data));
                        }
                    }
                }
                sink.complete();
            } catch (Exception e) {
                sink.error(e);
            }
        });
    }
}
```

### 选项 B：使用 LangChain4j（支持 Spring Boot 2.x）

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-spring-boot-starter</artifactId>
    <version>0.27.1</version>
</dependency>
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
    <version>0.27.1</version>
</dependency>
```

```java
@Configuration
public class LangChain4jConfig {
    @Bean
    public ChatLanguageModel chatModel() {
        return OpenAiChatModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .modelName("gpt-3.5-turbo")
            .temperature(0.7)
            .build();
    }
    
    @Bean
    public StreamingChatLanguageModel streamingChatModel() {
        return OpenAiStreamingChatModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .modelName("gpt-3.5-turbo")
            .build();
    }
}
```

### 优点
✅ 无需升级 Spring Boot
✅ 保持 Java 8
✅ 代码改动较小

### 缺点
⚠️ 失去 Spring AI 的便利性
⚠️ 需要自己实现更多功能
⚠️ 维护成本更高

---

## 推荐方案

### 🎯 短期方案（快速上线）
使用 **LangChain4j**，它支持 Spring Boot 2.x 和 Java 8，功能完善。

### 🎯 长期方案（推荐）
**升级到 Spring Boot 3.x + Java 17**，使用 Spring AI 1.0.0-M4（稳定的 Milestone 版本）。

---

## Spring AI OpenAI 的功能

Spring AI OpenAI Starter 提供以下核心功能：

### 1. 聊天完成（Chat Completion）
```java
@Autowired
private ChatClient chatClient;

// 同步调用
String response = chatClient.call("你好，请介绍一下自己");

// 流式调用
Flux<String> stream = chatClient.stream("写一篇关于 AI 的文章");
```

### 2. 嵌入生成（Embeddings）
```java
@Autowired
private EmbeddingClient embeddingClient;

// 生成文本向量
List<Double> embedding = embeddingClient.embed("这是一段文本");

// 批量生成
List<List<Double>> embeddings = embeddingClient.embed(
    List.of("文本1", "文本2", "文本3")
);
```

### 3. 图像生成（Image Generation）
```java
@Autowired
private ImageClient imageClient;

// 生成图像
ImageResponse response = imageClient.call(
    new ImagePrompt("一只可爱的猫咪")
);
```

### 4. 函数调用（Function Calling）
```java
@Bean
public Function<WeatherRequest, WeatherResponse> weatherFunction() {
    return request -> {
        // 调用天气 API
        return new WeatherResponse(request.getLocation(), "晴天", 25);
    };
}

// LLM 可以自动调用这个函数
String response = chatClient.call("北京今天天气怎么样？");
```

### 5. 提示词模板（Prompt Templates）
```java
PromptTemplate template = new PromptTemplate(
    "请用{language}语言回答：{question}"
);

Prompt prompt = template.create(Map.of(
    "language", "中文",
    "question", "什么是 AI？"
));

String response = chatClient.call(prompt);
```

### 6. 对话历史管理
```java
ChatMemory memory = new InMemoryChatMemory();

// 添加历史消息
memory.add(new UserMessage("你好"));
memory.add(new AssistantMessage("你好！有什么可以帮助你的？"));

// 带历史的对话
String response = chatClient.call(
    new Prompt(List.of(
        memory.getMessages(),
        new UserMessage("我刚才说了什么？")
    ))
);
```

### 7. 流式输出（SSE）
```java
@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> streamChat(@RequestParam String question) {
    return chatClient.stream(question);
}
```

### 8. 配置管理
```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      base-url: https://api.openai.com
      chat:
        options:
          model: gpt-3.5-turbo
          temperature: 0.7
          max-tokens: 2000
      embedding:
        options:
          model: text-embedding-ada-002
```

---

## 总结

1. **Spring AI 1.1.3 不兼容 Spring Boot 2.6.7**
2. **不要使用 1.0.0-SNAPSHOT**（不稳定）
3. **推荐方案**：
   - 短期：使用 LangChain4j（支持 Spring Boot 2.x）
   - 长期：升级到 Spring Boot 3.2 + Spring AI 1.0.0-M4
4. **Spring AI OpenAI 提供**：聊天、嵌入、图像生成、函数调用、流式输出等完整功能
