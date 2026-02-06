# LangChain4j 迁移方案

## 📋 迁移概览

### 迁移目标
将 AI 模块从 Spring AI 迁移到 LangChain4j，保持功能不变，无需升级 Spring Boot 和 Java 版本。

### 迁移范围
- ✅ LLM 集成模块（mallchat-ai-llm）
- ✅ 向量存储模块（mallchat-ai-vector）
- ✅ RAG 引擎模块（mallchat-ai-rag）
- ✅ 智能助手模块（mallchat-ai-assistant）
- ✅ 配置文件（application-ai.yml）

### 迁移时间估算
- 依赖调整：30 分钟
- 代码重构：2-3 小时
- 测试验证：1 小时
- **总计：3-4 小时**

---

## 🔄 迁移步骤

### 阶段 1：依赖调整（30 分钟）

#### 1.1 更新父 POM
```xml
<!-- mallchat-ai/pom.xml -->
<properties>
    <!-- 移除 Spring AI -->
    <!-- <spring-ai.version>1.1.3</spring-ai.version> -->
    
    <!-- 添加 LangChain4j -->
    <langchain4j.version>0.27.1</langchain4j.version>
    
    <!-- 保持不变 -->
    <milvus-sdk.version>2.3.4</milvus-sdk.version>
    <tika.version>2.9.1</tika.version>
    <jqwik.version>1.7.4</jqwik.version>
</properties>

<dependencyManagement>
    <dependencies>
        <!-- 移除 Spring AI OpenAI -->
        <!--
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
            <version>${spring-ai.version}</version>
        </dependency>
        -->
        
        <!-- 添加 LangChain4j -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>
        
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-open-ai</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>
        
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-embeddings-all-minilm-l6-v2</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>
        
        <!-- 其他依赖保持不变 -->
    </dependencies>
</dependencyManagement>
```

#### 1.2 更新 LLM 模块 POM
```xml
<!-- mallchat-ai/mallchat-ai-llm/pom.xml -->
<dependencies>
    <!-- 移除 Spring AI -->
    <!--
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
    </dependency>
    -->
    
    <!-- 添加 LangChain4j -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j</artifactId>
    </dependency>
    
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-open-ai</artifactId>
    </dependency>
    
    <!-- 保持其他依赖不变 -->
    <dependency>
        <groupId>com.abin.mallchat</groupId>
        <artifactId>mallchat-ai-common</artifactId>
    </dependency>
    
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>
    
    <!-- 测试依赖 -->
    <dependency>
        <groupId>net.jqwik</groupId>
        <artifactId>jqwik</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

#### 1.3 更新 Vector 模块 POM
```xml
<!-- mallchat-ai/mallchat-ai-vector/pom.xml -->
<dependencies>
    <!-- 添加 LangChain4j Embeddings -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j</artifactId>
    </dependency>
    
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-embeddings-all-minilm-l6-v2</artifactId>
    </dependency>
    
    <!-- Milvus SDK 保持不变 -->
    <dependency>
        <groupId>io.milvus</groupId>
        <artifactId>milvus-sdk-java</artifactId>
    </dependency>
    
    <dependency>
        <groupId>com.abin.mallchat</groupId>
        <artifactId>mallchat-ai-common</artifactId>
    </dependency>
</dependencies>
```

---

### 阶段 2：配置文件调整（15 分钟）

#### 2.1 更新 application-ai.yml
```yaml
# mallchat-chat-server/src/main/resources/application-ai.yml

# 移除 Spring AI 配置
# spring:
#   ai:
#     openai:
#       api-key: ${OPENAI_API_KEY}
#       base-url: https://api.openai.com
#       chat:
#         model: gpt-3.5-turbo
#         temperature: 0.7
#         max-tokens: 2000

# 添加 LangChain4j 配置
langchain4j:
  openai:
    # API 配置
    api-key: ${OPENAI_API_KEY:}
    base-url: https://api.openai.com/v1
    timeout: 60s
    max-retries: 3
    log-requests: true
    log-responses: false
    
    # 聊天模型配置
    chat-model:
      model-name: gpt-3.5-turbo
      temperature: 0.7
      max-tokens: 2000
      top-p: 1.0
      frequency-penalty: 0.0
      presence-penalty: 0.0
    
    # 流式聊天模型配置
    streaming-chat-model:
      model-name: gpt-3.5-turbo
      temperature: 0.7
      max-tokens: 2000
    
    # 嵌入模型配置
    embedding-model:
      model-name: text-embedding-ada-002
      dimensions: 1536

# Milvus 配置保持不变
milvus:
  host: ${MILVUS_HOST:localhost}
  port: ${MILVUS_PORT:19530}
  database: mallchat_ai
  collection:
    name: document_vectors
    dimension: 1536
    index-type: IVF_FLAT
    metric-type: L2
    nlist: 1024

# 文档处理配置保持不变
document:
  processing:
    chunk-size: 500
    chunk-overlap: 50
    max-file-size: 10485760  # 10MB
    allowed-types: txt,pdf,md,html,docx

# RAG 配置保持不变
rag:
  retrieval:
    top-k: 5
    score-threshold: 0.7
  prompt:
    system-instruction: "你是一个专业的AI助手，请基于提供的上下文回答问题。"
```

---

### 阶段 3：代码重构（2-3 小时）

#### 3.1 LLM Service 接口保持不变
```java
// mallchat-ai/mallchat-ai-llm/src/main/java/com/abin/mallchat/ai/llm/service/LLMService.java
package com.abin.mallchat.ai.llm.service;

import com.abin.mallchat.ai.llm.domain.LLMOptions;
import reactor.core.publisher.Flux;

/**
 * LLM 服务接口（保持不变）
 */
public interface LLMService {
    /**
     * 流式调用 LLM
     */
    Flux<String> streamChat(String prompt, LLMOptions options);
    
    /**
     * 非流式调用 LLM
     */
    String chat(String prompt, LLMOptions options);
    
    /**
     * 计算 token 数量
     */
    int countTokens(String text);
}
```

#### 3.2 LangChain4j 配置类（新增）
```java
// mallchat-ai/mallchat-ai-llm/src/main/java/com/abin/mallchat/ai/llm/config/LangChain4jConfig.java
package com.abin.mallchat.ai.llm.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LangChain4jConfig {
    
    @Value("${langchain4j.openai.api-key}")
    private String apiKey;
    
    @Value("${langchain4j.openai.base-url:https://api.openai.com/v1}")
    private String baseUrl;
    
    @Value("${langchain4j.openai.chat-model.model-name:gpt-3.5-turbo}")
    private String modelName;
    
    @Value("${langchain4j.openai.chat-model.temperature:0.7}")
    private Double temperature;
    
    @Value("${langchain4j.openai.chat-model.max-tokens:2000}")
    private Integer maxTokens;
    
    @Value("${langchain4j.openai.timeout:60s}")
    private Duration timeout;
    
    @Value("${langchain4j.openai.max-retries:3}")
    private Integer maxRetries;
    
    /**
     * 同步聊天模型
     */
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(timeout)
                .maxRetries(maxRetries)
                .logRequests(true)
                .logResponses(false)
                .build();
    }
    
    /**
     * 流式聊天模型
     */
    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel() {
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(timeout)
                .logRequests(true)
                .logResponses(false)
                .build();
    }
    
    /**
     * Token 计数器
     */
    @Bean
    public OpenAiTokenizer openAiTokenizer() {
        return new OpenAiTokenizer(modelName);
    }
}
```

#### 3.3 OpenAI LLM Service 实现（重构）
```java
// mallchat-ai/mallchat-ai-llm/src/main/java/com/abin/mallchat/ai/llm/service/impl/OpenAILLMService.java
package com.abin.mallchat.ai.llm.service.impl;

import com.abin.mallchat.ai.llm.domain.LLMOptions;
import com.abin.mallchat.ai.llm.service.LLMService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * OpenAI LLM 服务实现（使用 LangChain4j）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAILLMService implements LLMService {
    
    private final ChatLanguageModel chatLanguageModel;
    private final StreamingChatLanguageModel streamingChatLanguageModel;
    private final OpenAiTokenizer tokenizer;
    
    @Override
    public Flux<String> streamChat(String prompt, LLMOptions options) {
        log.info("开始流式调用 LLM, prompt 长度: {}", prompt.length());
        
        return Flux.create(sink -> {
            try {
                streamingChatLanguageModel.generate(
                    prompt,
                    new StreamingResponseHandler<AiMessage>() {
                        @Override
                        public void onNext(String token) {
                            sink.next(token);
                        }
                        
                        @Override
                        public void onComplete(Response<AiMessage> response) {
                            log.info("LLM 流式调用完成");
                            sink.complete();
                        }
                        
                        @Override
                        public void onError(Throwable error) {
                            log.error("LLM 流式调用失败", error);
                            sink.error(error);
                        }
                    }
                );
            } catch (Exception e) {
                log.error("启动 LLM 流式调用失败", e);
                sink.error(e);
            }
        });
    }
    
    @Override
    public String chat(String prompt, LLMOptions options) {
        log.info("开始同步调用 LLM, prompt 长度: {}", prompt.length());
        
        try {
            String response = chatLanguageModel.generate(prompt);
            log.info("LLM 同步调用完成, 响应长度: {}", response.length());
            return response;
        } catch (Exception e) {
            log.error("LLM 同步调用失败", e);
            throw new RuntimeException("LLM 调用失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public int countTokens(String text) {
        return tokenizer.estimateTokenCountInText(text);
    }
}
```

#### 3.4 Embedding Service 接口（新增）
```java
// mallchat-ai/mallchat-ai-vector/src/main/java/com/abin/mallchat/ai/vector/service/EmbeddingService.java
package com.abin.mallchat.ai.vector.service;

import java.util.List;

/**
 * 嵌入服务接口
 */
public interface EmbeddingService {
    /**
     * 生成单个文本的向量
     */
    float[] generateEmbedding(String text);
    
    /**
     * 批量生成向量
     */
    List<float[]> generateEmbeddings(List<String> texts);
}
```

#### 3.5 Embedding Service 实现（新增）
```java
// mallchat-ai/mallchat-ai-vector/src/main/java/com/abin/mallchat/ai/vector/service/impl/OpenAIEmbeddingService.java
package com.abin.mallchat.ai.vector.service.impl;

import com.abin.mallchat.ai.vector.service.EmbeddingService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.stream.Collectors;

/**
 * OpenAI 嵌入服务实现
 */
@Slf4j
@Service
public class OpenAIEmbeddingService implements EmbeddingService {
    
    @Value("${langchain4j.openai.api-key}")
    private String apiKey;
    
    @Value("${langchain4j.openai.embedding-model.model-name:text-embedding-ada-002}")
    private String modelName;
    
    private EmbeddingModel embeddingModel;
    
    @PostConstruct
    public void init() {
        this.embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .build();
    }
    
    @Override
    public float[] generateEmbedding(String text) {
        log.debug("生成文本向量, 文本长度: {}", text.length());
        
        Response<Embedding> response = embeddingModel.embed(text);
        float[] vector = response.content().vector();
        
        log.debug("向量生成完成, 维度: {}", vector.length);
        return vector;
    }
    
    @Override
    public List<float[]> generateEmbeddings(List<String> texts) {
        log.info("批量生成向量, 数量: {}", texts.size());
        
        return texts.stream()
                .map(this::generateEmbedding)
                .collect(Collectors.toList());
    }
}
```

---

### 阶段 4：测试代码调整（30 分钟）

#### 4.1 更新单元测试
```java
// mallchat-ai/mallchat-ai-llm/src/test/java/com/abin/mallchat/ai/llm/service/impl/OpenAILLMServiceTest.java
package com.abin.mallchat.ai.llm.service.impl;

import com.abin.mallchat.ai.llm.domain.LLMOptions;
import com.abin.mallchat.ai.llm.service.LLMService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OpenAILLMServiceTest {
    
    @Autowired
    private LLMService llmService;
    
    @Test
    void testStreamChat() {
        // Given
        String prompt = "请用一句话介绍 Java";
        LLMOptions options = new LLMOptions();
        
        // When
        Flux<String> response = llmService.streamChat(prompt, options);
        
        // Then
        StepVerifier.create(response)
                .expectNextMatches(token -> token != null && !token.isEmpty())
                .thenConsumeWhile(token -> true)
                .verifyComplete();
    }
    
    @Test
    void testChat() {
        // Given
        String prompt = "请用一句话介绍 Java";
        LLMOptions options = new LLMOptions();
        
        // When
        String response = llmService.chat(prompt, options);
        
        // Then
        assertThat(response).isNotEmpty();
        assertThat(response).contains("Java");
    }
    
    @Test
    void testCountTokens() {
        // Given
        String text = "Hello, world!";
        
        // When
        int tokenCount = llmService.countTokens(text);
        
        // Then
        assertThat(tokenCount).isGreaterThan(0);
    }
}
```

#### 4.2 属性测试保持不变
```java
// 属性测试代码无需修改，因为接口没有变化
// mallchat-ai/mallchat-ai-llm/src/test/java/com/abin/mallchat/ai/llm/service/impl/StreamResponseConsistencyPropertyTest.java
// 保持原样即可
```

---

### 阶段 5：验证测试（1 小时）

#### 5.1 编译验证
```bash
cd mallchat-ai
mvn clean compile
```

#### 5.2 单元测试
```bash
mvn test
```

#### 5.3 集成测试
```bash
# 启动应用
cd ../mallchat-chat-server
mvn spring-boot:run -Dspring.profiles.active=local,ai

# 测试 LLM 调用
curl -X POST http://localhost:8080/api/ai/assistant/question \
  -H "Content-Type: application/json" \
  -d '{"question": "你好，请介绍一下自己"}'
```

---

## 📊 迁移对比

### 代码变化统计

| 文件类型 | 修改文件数 | 新增文件数 | 删除文件数 |
|---------|-----------|-----------|-----------|
| POM 文件 | 4 | 0 | 0 |
| 配置文件 | 1 | 0 | 0 |
| Java 类 | 2 | 2 | 0 |
| 测试类 | 1 | 0 | 0 |
| **总计** | **8** | **2** | **0** |

### API 对比

| 功能 | Spring AI | LangChain4j | 变化 |
|------|-----------|-------------|------|
| 流式聊天 | `ChatClient.stream()` | `StreamingChatLanguageModel.generate()` | ⚠️ API 变化 |
| 同步聊天 | `ChatClient.call()` | `ChatLanguageModel.generate()` | ⚠️ API 变化 |
| 生成向量 | `EmbeddingClient.embed()` | `EmbeddingModel.embed()` | ⚠️ API 变化 |
| Token 计数 | 需自己实现 | `OpenAiTokenizer.estimateTokenCountInText()` | ✅ 更方便 |

---

## ⚠️ 注意事项

### 1. 环境变量
确保设置了 OpenAI API Key：
```bash
export OPENAI_API_KEY=sk-your-api-key
```

### 2. 依赖冲突
如果遇到依赖冲突，检查：
```bash
mvn dependency:tree | grep langchain4j
```

### 3. 日志配置
LangChain4j 的日志级别：
```yaml
logging:
  level:
    dev.langchain4j: DEBUG
```

### 4. 超时配置
根据实际情况调整超时时间：
```yaml
langchain4j:
  openai:
    timeout: 120s  # 增加到 2 分钟
```

---

## 🎯 迁移检查清单

- [ ] 更新所有 POM 文件
- [ ] 更新配置文件 application-ai.yml
- [ ] 创建 LangChain4jConfig 配置类
- [ ] 重构 OpenAILLMService 实现
- [ ] 创建 EmbeddingService 接口和实现
- [ ] 更新单元测试
- [ ] 运行所有测试确保通过
- [ ] 启动应用验证功能
- [ ] 测试流式输出
- [ ] 测试向量生成
- [ ] 更新文档

---

## 📚 相关文档

- [LangChain4j 官方文档](https://docs.langchain4j.dev/)
- [LangChain4j GitHub](https://github.com/langchain4j/langchain4j)
- [OpenAI API 文档](https://platform.openai.com/docs/api-reference)

---

## 🆘 常见问题

### Q1: 依赖下载失败怎么办？
A: 检查 Maven 仓库配置，确保可以访问 Maven Central。

### Q2: API Key 无效怎么办？
A: 检查环境变量是否正确设置，或在配置文件中直接配置（不推荐）。

### Q3: 流式输出不工作怎么办？
A: 检查是否正确使用了 `StreamingChatLanguageModel` 和 `StreamingResponseHandler`。

### Q4: Token 计数不准确怎么办？
A: `OpenAiTokenizer` 是估算值，实际消耗以 OpenAI API 返回为准。

---

## ✅ 迁移完成标志

当以下所有项都完成时，迁移即告成功：

1. ✅ 所有测试通过
2. ✅ 应用可以正常启动
3. ✅ LLM 调用正常工作
4. ✅ 流式输出正常工作
5. ✅ 向量生成正常工作
6. ✅ 无编译错误和警告
7. ✅ 日志输出正常
8. ✅ 性能符合预期

恭喜！你已成功迁移到 LangChain4j！🎉
