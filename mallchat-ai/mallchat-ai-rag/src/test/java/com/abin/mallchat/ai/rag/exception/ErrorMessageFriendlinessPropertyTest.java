package com.abin.mallchat.ai.rag.exception;

import com.abin.mallchat.ai.common.exception.*;
import com.abin.mallchat.common.common.domain.vo.response.ApiResult;
import net.jqwik.api.*;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature: ai-assistant-rag, Property 4: Error Message Friendliness
 * Validates: Requirements 1.4, 11.1, 11.5
 * 
 * 验证错误消息人类可读，验证不暴露内部信息
 * 
 * @author zxw
 */
class ErrorMessageFriendlinessPropertyTest {
    
    private AIGlobalExceptionHandler exceptionHandler;
    
    // 敏感信息模式
    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            "(?i)(password|token|secret|key|api[_-]?key|access[_-]?token)\\s*[=:]\\s*[^\\s,]+");
    
    // 路径信息模式
    private static final Pattern PATH_PATTERN = Pattern.compile(
            "(?i)(/[a-z]:/|/home/|/usr/|/var/|C:\\\\|D:\\\\)");
    
    // 堆栈信息模式
    private static final Pattern STACK_TRACE_PATTERN = Pattern.compile(
            "(?i)(at\\s+[a-z0-9_.]+\\([a-z0-9_.]+\\.java:\\d+\\))");
    
    // SQL语句模式
    private static final Pattern SQL_PATTERN = Pattern.compile(
            "(?i)(select|insert|update|delete|from|where)\\s+.*\\s+(from|where|set)");
    
    @BeforeEach
    void setUp() {
        exceptionHandler = new AIGlobalExceptionHandler();
        
        // 设置请求上下文
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/ai/test");
        request.setMethod("POST");
        ServletRequestAttributes attributes = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attributes);
    }
    
    /**
     * Property 4.1: LLM异常消息应该友好且不暴露内部信息
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 4.1: LLM exception messages should be friendly")
    void llmExceptionMessagesShouldBeFriendly(
            @ForAll("llmErrorEnums") AIErrorEnum errorEnum) {
        
        // Given: LLM异常
        LLMException exception = new LLMException(errorEnum);
        
        // When: 处理异常
        ApiResult<Void> result = exceptionHandler.handleLLMException(exception);
        
        // Then: 验证错误消息友好性
        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getErrMsg()).isNotNull();
        assertThat(result.getErrMsg()).isNotEmpty();
        
        // 验证不包含敏感信息
        assertThat(result.getErrMsg()).doesNotContainPattern(SENSITIVE_PATTERN);
        assertThat(result.getErrMsg()).doesNotContainPattern(PATH_PATTERN);
        assertThat(result.getErrMsg()).doesNotContainPattern(STACK_TRACE_PATTERN);
        assertThat(result.getErrMsg()).doesNotContainPattern(SQL_PATTERN);
        
        // 验证消息长度合理（不超过200字符）
        assertThat(result.getErrMsg().length()).isLessThanOrEqualTo(200);
        
        // 验证消息是中文友好提示
        assertThat(result.getErrMsg()).matches(".*[\\u4e00-\\u9fa5]+.*");
    }
    
    /**
     * Property 4.2: 向量存储异常消息应该友好
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 4.2: Vector store exception messages should be friendly")
    void vectorStoreExceptionMessagesShouldBeFriendly(
            @ForAll("vectorStoreErrorEnums") AIErrorEnum errorEnum) {
        
        // Given: 向量存储异常
        VectorStoreException exception = new VectorStoreException(errorEnum);
        
        // When: 处理异常
        ApiResult<Void> result = exceptionHandler.handleVectorStoreException(exception);
        
        // Then: 验证错误消息友好性
        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getErrMsg()).isNotNull();
        assertThat(result.getErrMsg()).isNotEmpty();
        assertThat(result.getErrMsg()).doesNotContainPattern(SENSITIVE_PATTERN);
        assertThat(result.getErrMsg()).doesNotContainPattern(PATH_PATTERN);
        assertThat(result.getErrMsg()).doesNotContainPattern(STACK_TRACE_PATTERN);
        assertThat(result.getErrMsg().length()).isLessThanOrEqualTo(200);
    }
    
    /**
     * Property 4.3: 文档处理异常消息应该友好
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 4.3: Document processing exception messages should be friendly")
    void documentProcessingExceptionMessagesShouldBeFriendly(
            @ForAll("documentErrorEnums") AIErrorEnum errorEnum) {
        
        // Given: 文档处理异常
        DocumentProcessingException exception = new DocumentProcessingException(errorEnum);
        
        // When: 处理异常
        ApiResult<Void> result = exceptionHandler.handleDocumentProcessingException(exception);
        
        // Then: 验证错误消息友好性
        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getErrMsg()).isNotNull();
        assertThat(result.getErrMsg()).isNotEmpty();
        assertThat(result.getErrMsg()).doesNotContainPattern(SENSITIVE_PATTERN);
        assertThat(result.getErrMsg()).doesNotContainPattern(PATH_PATTERN);
        assertThat(result.getErrMsg()).doesNotContainPattern(STACK_TRACE_PATTERN);
        assertThat(result.getErrMsg().length()).isLessThanOrEqualTo(200);
    }
    
    /**
     * Property 4.4: 非法参数异常应该清理敏感信息
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 4.4: Illegal argument exceptions should sanitize sensitive info")
    void illegalArgumentExceptionsShouldSanitizeSensitiveInfo(
            @ForAll("sensitiveMessages") String sensitiveMessage) {
        
        // Given: 包含敏感信息的异常
        IllegalArgumentException exception = new IllegalArgumentException(sensitiveMessage);
        
        // When: 处理异常
        ApiResult<Void> result = exceptionHandler.handleIllegalArgumentException(exception);
        
        // Then: 验证敏感信息被清理
        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getErrMsg()).isNotNull();
        
        // 验证不包含原始敏感信息
        if (sensitiveMessage.contains("password")) {
            assertThat(result.getErrMsg()).doesNotContain("password=123456");
            assertThat(result.getErrMsg()).doesNotContain("password:123456");
        }
        if (sensitiveMessage.contains("token")) {
            assertThat(result.getErrMsg()).doesNotContain("token=abc123");
            assertThat(result.getErrMsg()).doesNotContain("token:abc123");
        }
    }
    
    /**
     * Property 4.5: 超时异常消息应该友好
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 4.5: Timeout exception messages should be friendly")
    void timeoutExceptionMessagesShouldBeFriendly() {
        // Given: 超时异常
        TimeoutException exception = new TimeoutException("Operation timed out after 30 seconds");
        
        // When: 处理异常
        ApiResult<Void> result = exceptionHandler.handleTimeoutException(exception);
        
        // Then: 验证错误消息友好性
        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getErrMsg()).isNotNull();
        assertThat(result.getErrMsg()).isEqualTo(AIErrorEnum.TIMEOUT_ERROR.getErrorMsg());
        assertThat(result.getErrMsg()).matches(".*[\\u4e00-\\u9fa5]+.*");
    }
    
    /**
     * Property 4.6: 空指针异常不应该暴露内部信息
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 4.6: NPE should not expose internal information")
    void nullPointerExceptionShouldNotExposeInternalInfo() {
        // Given: 空指针异常
        NullPointerException exception = new NullPointerException(
                "Cannot invoke method on null object at com.abin.internal.Service.method(Service.java:123)");
        
        // When: 处理异常
        ApiResult<Void> result = exceptionHandler.handleNullPointerException(exception);
        
        // Then: 验证不暴露内部信息
        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getErrMsg()).isNotNull();
        assertThat(result.getErrMsg()).isEqualTo(AIErrorEnum.SYSTEM_ERROR.getErrorMsg());
        
        // 验证不包含堆栈信息
        assertThat(result.getErrMsg()).doesNotContain("Service.java");
        assertThat(result.getErrMsg()).doesNotContain("com.abin.internal");
        assertThat(result.getErrMsg()).doesNotContainPattern(STACK_TRACE_PATTERN);
    }
    
    /**
     * Property 4.7: 未知异常不应该暴露内部信息
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 4.7: Unknown exceptions should not expose internal information")
    void unknownExceptionsShouldNotExposeInternalInfo(
            @ForAll @StringLength(min = 10, max = 200) String internalMessage) {
        
        // Given: 未知异常
        Exception exception = new Exception(internalMessage);
        
        // When: 处理异常
        ApiResult<Void> result = exceptionHandler.handleUnknownException(exception);
        
        // Then: 验证不暴露内部信息
        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getErrMsg()).isNotNull();
        assertThat(result.getErrMsg()).isEqualTo(AIErrorEnum.SYSTEM_ERROR.getErrorMsg());
        
        // 验证不包含原始异常消息
        assertThat(result.getErrMsg()).doesNotContain(internalMessage);
    }
    
    /**
     * Property 4.8: 所有错误响应都应该有正确的HTTP状态码
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 4.8: All error responses should have correct HTTP status")
    void allErrorResponsesShouldHaveCorrectHttpStatus(
            @ForAll("allErrorEnums") AIErrorEnum errorEnum) {
        
        // Given: AI异常
        AIException exception = new AIException(errorEnum);
        
        // When: 处理异常
        ApiResult<Void> result = exceptionHandler.handleAIException(exception);
        
        // Then: 验证响应结构正确
        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getErrCode()).isNotNull();
        assertThat(result.getErrMsg()).isNotNull();
        assertThat(result.getErrCode()).isEqualTo(errorEnum.getErrorCode());
    }
    
    /**
     * Property 4.9: 错误消息长度应该合理
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 4.9: Error message length should be reasonable")
    void errorMessageLengthShouldBeReasonable(
            @ForAll("allErrorEnums") AIErrorEnum errorEnum) {
        
        // Given: 错误枚举
        String errorMsg = errorEnum.getErrorMsg();
        
        // Then: 验证消息长度合理
        assertThat(errorMsg).isNotNull();
        assertThat(errorMsg).isNotEmpty();
        assertThat(errorMsg.length()).isGreaterThan(5); // 至少有意义的提示
        assertThat(errorMsg.length()).isLessThanOrEqualTo(200); // 不超过200字符
    }
    
    /**
     * Property 4.10: 错误消息应该包含中文
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 4.10: Error messages should contain Chinese characters")
    void errorMessagesShouldContainChinese(
            @ForAll("allErrorEnums") AIErrorEnum errorEnum) {
        
        // Given: 错误枚举
        String errorMsg = errorEnum.getErrorMsg();
        
        // Then: 验证包含中文字符
        assertThat(errorMsg).matches(".*[\\u4e00-\\u9fa5]+.*");
    }
    
    // ========== Arbitraries ==========
    
    @Provide
    Arbitrary<AIErrorEnum> llmErrorEnums() {
        return Arbitraries.of(
                AIErrorEnum.LLM_API_ERROR,
                AIErrorEnum.LLM_TIMEOUT,
                AIErrorEnum.LLM_RATE_LIMIT,
                AIErrorEnum.LLM_INVALID_RESPONSE,
                AIErrorEnum.TOKEN_LIMIT_EXCEEDED
        );
    }
    
    @Provide
    Arbitrary<AIErrorEnum> vectorStoreErrorEnums() {
        return Arbitraries.of(
                AIErrorEnum.VECTOR_STORE_ERROR,
                AIErrorEnum.VECTOR_STORE_TIMEOUT,
                AIErrorEnum.VECTOR_SEARCH_ERROR,
                AIErrorEnum.EMBEDDING_GENERATION_ERROR
        );
    }
    
    @Provide
    Arbitrary<AIErrorEnum> documentErrorEnums() {
        return Arbitraries.of(
                AIErrorEnum.DOCUMENT_PARSE_ERROR,
                AIErrorEnum.DOCUMENT_TOO_LARGE,
                AIErrorEnum.DOCUMENT_FORMAT_UNSUPPORTED,
                AIErrorEnum.DOCUMENT_NOT_FOUND,
                AIErrorEnum.DOCUMENT_INDEXING_ERROR
        );
    }
    
    @Provide
    Arbitrary<AIErrorEnum> allErrorEnums() {
        return Arbitraries.of(AIErrorEnum.values());
    }
    
    @Provide
    Arbitrary<String> sensitiveMessages() {
        return Arbitraries.of(
                "Invalid password=123456 provided",
                "Token: abc123def456 is expired",
                "API key=sk-1234567890abcdef failed",
                "Access token:bearer_token_xyz invalid",
                "Secret key = my_secret_key_123",
                "Database password: db_pass_456"
        );
    }
}
