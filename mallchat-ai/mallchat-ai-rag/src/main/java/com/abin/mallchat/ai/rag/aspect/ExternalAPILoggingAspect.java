package com.abin.mallchat.ai.rag.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 外部 API 调用日志记录切面
 * 记录 LLM API 和向量数据库调用的详细信息
 * 
 * @author AI Assistant
 * @date 2025-01-07
 */
@Slf4j
@Aspect
@Component
public class ExternalAPILoggingAspect {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 定义切点：拦截 LLM Service 的方法
     */
    @Pointcut("execution(* com.abin.mallchat.ai.llm.service..*(..))")
    public void llmServiceMethods() {}

    /**
     * 定义切点：拦截 Vector Service 的方法
     */
    @Pointcut("execution(* com.abin.mallchat.ai.vector.service..*(..))")
    public void vectorServiceMethods() {}

    /**
     * 环绕通知：记录外部 API 调用详情
     */
    @Around("llmServiceMethods() || vectorServiceMethods()")
    public Object logExternalAPICall(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        String callId = generateCallId();
        
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String serviceName = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();
        Object[] args = joinPoint.getArgs();
        
        // 确定 API 类型
        String apiType = serviceName.contains("LLM") ? "LLM" : "VECTOR_STORE";
        
        // 记录调用开始
        logAPICallStart(callId, apiType, serviceName, methodName, args);
        
        Object result = null;
        String status = "SUCCESS";
        Throwable exception = null;
        int responseSize = 0;
        
        try {
            result = joinPoint.proceed();
            
            // 估算响应大小
            if (result != null) {
                responseSize = estimateSize(result);
            }
            
            return result;
        } catch (Throwable e) {
            status = "FAILED";
            exception = e;
            throw e;
        } finally {
            long latency = System.currentTimeMillis() - startTime;
            // 记录调用结束
            logAPICallEnd(callId, apiType, serviceName, methodName, latency, status, responseSize, exception);
        }
    }

    /**
     * 记录 API 调用开始
     */
    private void logAPICallStart(String callId, String apiType, String serviceName, 
                                  String methodName, Object[] args) {
        try {
            Map<String, Object> logData = new HashMap<>();
            logData.put("callId", callId);
            logData.put("apiType", apiType);
            logData.put("service", serviceName);
            logData.put("method", methodName);
            logData.put("timestamp", System.currentTimeMillis());
            
            // 记录请求大小（估算）
            int requestSize = 0;
            if (args != null) {
                for (Object arg : args) {
                    requestSize += estimateSize(arg);
                }
            }
            logData.put("requestSize", requestSize);
            
            log.info("[EXTERNAL-API-START] {}", objectMapper.writeValueAsString(logData));
        } catch (Exception e) {
            log.warn("Failed to log API call start", e);
        }
    }

    /**
     * 记录 API 调用结束
     */
    private void logAPICallEnd(String callId, String apiType, String serviceName, String methodName,
                                long latency, String status, int responseSize, Throwable exception) {
        try {
            Map<String, Object> logData = new HashMap<>();
            logData.put("callId", callId);
            logData.put("apiType", apiType);
            logData.put("service", serviceName);
            logData.put("method", methodName);
            logData.put("latency", latency);
            logData.put("status", status);
            logData.put("responseSize", responseSize);
            logData.put("timestamp", System.currentTimeMillis());
            
            if (exception != null) {
                logData.put("errorType", exception.getClass().getSimpleName());
                logData.put("errorMessage", exception.getMessage());
            }
            
            // 根据延迟判断是否需要警告
            if (latency > 5000) {
                log.warn("[EXTERNAL-API-END] Slow API call detected: {}", objectMapper.writeValueAsString(logData));
            } else if ("FAILED".equals(status)) {
                log.error("[EXTERNAL-API-END] {}", objectMapper.writeValueAsString(logData));
            } else {
                log.info("[EXTERNAL-API-END] {}", objectMapper.writeValueAsString(logData));
            }
        } catch (Exception e) {
            log.warn("Failed to log API call end", e);
        }
    }

    /**
     * 估算对象大小（字节）
     */
    private int estimateSize(Object obj) {
        if (obj == null) {
            return 0;
        }
        
        if (obj instanceof String) {
            return ((String) obj).length();
        } else if (obj instanceof byte[]) {
            return ((byte[]) obj).length;
        } else if (obj instanceof float[]) {
            return ((float[]) obj).length * 4;
        } else {
            // 其他类型返回默认估算值
            return 100;
        }
    }

    /**
     * 生成调用 ID
     */
    private String generateCallId() {
        return "API-" + System.currentTimeMillis() + "-" + Thread.currentThread().getId();
    }
}
