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
 * AI 请求日志记录切面
 * 使用 AOP 记录所有 AI 相关请求的详细信息
 * 
 * @author AI Assistant
 * @date 2025-01-07
 */
@Slf4j
@Aspect
@Component
public class AIRequestLoggingAspect {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 定义切点：拦截所有 controller 包下的方法
     */
    @Pointcut("execution(* com.abin.mallchat.ai.rag.controller..*(..))")
    public void controllerMethods() {}

    /**
     * 定义切点：拦截所有 service 包下的方法
     */
    @Pointcut("execution(* com.abin.mallchat.ai.rag.service..*(..))")
    public void serviceMethods() {}

    /**
     * 环绕通知：记录请求详情
     */
    @Around("controllerMethods() || serviceMethods()")
    public Object logAIRequest(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        String requestId = generateRequestId();
        
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();
        Object[] args = joinPoint.getArgs();
        
        // 记录请求开始
        logRequestStart(requestId, className, methodName, args);
        
        Object result = null;
        String status = "SUCCESS";
        Throwable exception = null;
        
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable e) {
            status = "FAILED";
            exception = e;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            // 记录请求结束
            logRequestEnd(requestId, className, methodName, duration, status, exception);
        }
    }

    /**
     * 记录请求开始
     */
    private void logRequestStart(String requestId, String className, String methodName, Object[] args) {
        try {
            Map<String, Object> logData = new HashMap<>();
            logData.put("requestId", requestId);
            logData.put("className", className);
            logData.put("methodName", methodName);
            logData.put("timestamp", System.currentTimeMillis());
            
            // 记录参数（避免记录敏感信息）
            if (args != null && args.length > 0) {
                logData.put("argsCount", args.length);
                // 只记录参数类型，不记录具体值（避免日志过大）
                String[] argTypes = new String[args.length];
                for (int i = 0; i < args.length; i++) {
                    argTypes[i] = args[i] != null ? args[i].getClass().getSimpleName() : "null";
                }
                logData.put("argTypes", argTypes);
            }
            
            log.info("[AI-REQUEST-START] {}", objectMapper.writeValueAsString(logData));
        } catch (Exception e) {
            log.warn("Failed to log request start", e);
        }
    }

    /**
     * 记录请求结束
     */
    private void logRequestEnd(String requestId, String className, String methodName, 
                                long duration, String status, Throwable exception) {
        try {
            Map<String, Object> logData = new HashMap<>();
            logData.put("requestId", requestId);
            logData.put("className", className);
            logData.put("methodName", methodName);
            logData.put("duration", duration);
            logData.put("status", status);
            logData.put("timestamp", System.currentTimeMillis());
            
            if (exception != null) {
                logData.put("errorType", exception.getClass().getSimpleName());
                logData.put("errorMessage", exception.getMessage());
            }
            
            if ("FAILED".equals(status)) {
                log.error("[AI-REQUEST-END] {}", objectMapper.writeValueAsString(logData));
            } else {
                log.info("[AI-REQUEST-END] {}", objectMapper.writeValueAsString(logData));
            }
        } catch (Exception e) {
            log.warn("Failed to log request end", e);
        }
    }

    /**
     * 生成请求 ID
     */
    private String generateRequestId() {
        return "AI-" + System.currentTimeMillis() + "-" + Thread.currentThread().getId();
    }
}
