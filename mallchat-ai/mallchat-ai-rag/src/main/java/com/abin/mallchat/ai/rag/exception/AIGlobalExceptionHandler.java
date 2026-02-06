package com.abin.mallchat.ai.rag.exception;

import com.abin.mallchat.ai.common.exception.*;
import com.abin.mallchat.common.common.domain.vo.response.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * AI模块全局异常处理器
 * 
 * 职责：
 * 1. 捕获并处理AI模块的各类异常
 * 2. 返回友好的错误消息（不暴露内部细节）
 * 3. 记录详细的错误日志用于排查问题
 * 
 * @author Kiro
 */
@RestControllerAdvice(basePackages = "com.abin.mallchat.ai")
@Slf4j
public class AIGlobalExceptionHandler {

    /**
     * LLM服务异常处理
     * 场景：LLM API调用失败、超时、限流等
     */
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    @ExceptionHandler(value = LLMException.class)
    public ApiResult<Void> handleLLMException(LLMException e) {
        logError("LLM服务异常", e);
        // 返回友好提示，不暴露内部错误
        return ApiResult.fail(e.getErrorCode(), e.getErrorMsg());
    }

    /**
     * 向量存储异常处理
     * 场景：向量数据库连接失败、检索超时等
     */
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    @ExceptionHandler(value = VectorStoreException.class)
    public ApiResult<Void> handleVectorStoreException(VectorStoreException e) {
        logError("向量存储异常", e);
        return ApiResult.fail(e.getErrorCode(), e.getErrorMsg());
    }

    /**
     * 文档处理异常处理
     * 场景：文档解析失败、格式不支持等
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(value = DocumentProcessingException.class)
    public ApiResult<Void> handleDocumentProcessingException(DocumentProcessingException e) {
        logError("文档处理异常", e);
        return ApiResult.fail(e.getErrorCode(), e.getErrorMsg());
    }

    /**
     * AI通用业务异常处理
     * 场景：索引未就绪、输入验证失败等
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(value = AIException.class)
    public ApiResult<Void> handleAIException(AIException e) {
        logError("AI业务异常", e);
        return ApiResult.fail(e.getErrorCode(), e.getErrorMsg());
    }

    /**
     * 参数校验异常处理
     * 场景：@Valid注解校验失败
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public ApiResult<Void> handleValidationException(MethodArgumentNotValidException e) {
        List<FieldError> errors = e.getBindingResult().getFieldErrors();
        if (errors.isEmpty()) {
            return ApiResult.fail(AIErrorEnum.INVALID_INPUT.getErrorCode(), "参数校验失败");
        }

        String errorMsg = errors.stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        logWarn("参数校验失败", errorMsg);
        return ApiResult.fail(AIErrorEnum.INVALID_INPUT.getErrorCode(), errorMsg);
    }

    /**
     * 绑定异常处理
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(value = BindException.class)
    public ApiResult<Void> handleBindException(BindException e) {
        String errorMsg = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        logWarn("参数绑定失败", errorMsg);
        return ApiResult.fail(AIErrorEnum.INVALID_INPUT.getErrorCode(), errorMsg);
    }

    /**
     * 超时异常处理
     * 场景：操作超时
     */
    @ResponseStatus(HttpStatus.REQUEST_TIMEOUT)
    @ExceptionHandler(value = TimeoutException.class)
    public ApiResult<Void> handleTimeoutException(TimeoutException e) {
        logError("操作超时", e);
        // 使用 .getErrorCode()/.getErrorMsg() 与 project 中 ApiResult 的签名兼容
        return ApiResult.fail(AIErrorEnum.TIMEOUT_ERROR.getErrorCode(), AIErrorEnum.TIMEOUT_ERROR.getErrorMsg());
    }

    /**
     * 非法参数异常处理
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(value = IllegalArgumentException.class)
    public ApiResult<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        logWarn("非法参数", e.getMessage());
        return ApiResult.fail(AIErrorEnum.INVALID_INPUT.getErrorCode(), 
                             "参数不合法: " + sanitizeErrorMessage(e.getMessage()));
    }

    /**
     * 空指针异常处理
     * 注意：这通常是代码bug，需要修复
     */
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(value = NullPointerException.class)
    public ApiResult<Void> handleNullPointerException(NullPointerException e) {
        logError("空指针异常", e);
        // 不暴露具体错误信息 - 使用 code/msg 形式
        return ApiResult.fail(AIErrorEnum.SYSTEM_ERROR.getErrorCode(), AIErrorEnum.SYSTEM_ERROR.getErrorMsg());
    }

    /**
     * 未知异常处理
     * 兜底处理，捕获所有未处理的异常
     */
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(value = Exception.class)
    public ApiResult<Void> handleUnknownException(Exception e) {
        logError("未知异常", e);
        // 不暴露具体错误信息 - 使用 code/msg 形式
        return ApiResult.fail(AIErrorEnum.SYSTEM_ERROR.getErrorCode(), AIErrorEnum.SYSTEM_ERROR.getErrorMsg());
    }

    /**
     * 记录错误日志（包含请求上下文）
     */
    private void logError(String errorType, Exception e) {
        try {
            ServletRequestAttributes attributes = 
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                log.error("[AI异常] {} - URL: {}, Method: {}, 错误: {}", 
                         errorType, 
                         request.getRequestURI(), 
                         request.getMethod(),
                         e.getMessage(), 
                         e);
            } else {
                log.error("[AI异常] {} - 错误: {}", errorType, e.getMessage(), e);
            }
        } catch (Exception ex) {
            // 防止日志记录失败影响异常处理
            log.error("[AI异常] {} - 错误: {}", errorType, e.getMessage(), e);
        }
    }

    /**
     * 记录警告日志
     */
    private void logWarn(String warnType, String message) {
        try {
            ServletRequestAttributes attributes = 
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                log.warn("[AI警告] {} - URL: {}, Method: {}, 详情: {}", 
                        warnType, 
                        request.getRequestURI(), 
                        request.getMethod(),
                        message);
            } else {
                log.warn("[AI警告] {} - 详情: {}", warnType, message);
            }
        } catch (Exception ex) {
            log.warn("[AI警告] {} - 详情: {}", warnType, message);
        }
    }

    /**
     * 清理错误消息，防止暴露敏感信息
     * 移除可能包含的路径、SQL、堆栈信息等
     */
    private String sanitizeErrorMessage(String message) {
        if (message == null) {
            return "未知错误";
        }
        
        // 限制长度
        if (message.length() > 200) {
            message = message.substring(0, 200) + "...";
        }
        
        // 移除可能的敏感信息
        message = message.replaceAll("(?i)(password|token|secret|key)\\s*[=:>]\\s*\\S+", "$1=***");
        message = message.replaceAll("(?i)(/[a-z]:/|/home/|/usr/|/var/)", "[PATH]/");
        
        return message;
    }
}
