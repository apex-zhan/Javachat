package com.abin.mallchat.ai.rag.aspect;

import com.abin.mallchat.ai.rag.metrics.VectorSearchMetrics;
import com.abin.mallchat.ai.vector.domain.SearchResult;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 向量检索指标记录切面
 * 自动记录所有向量检索操作的指标
 * 
 * @author AI Assistant
 * @date 2025-01-07
 */
@Slf4j
@Aspect
@Component
public class VectorSearchMetricsAspect {

    @Autowired
    private VectorSearchMetrics vectorSearchMetrics;

    /**
     * 定义切点：拦截 VectorService 的 search 方法
     */
    @Pointcut("execution(* com.abin.mallchat.ai.vector.service..search(..))")
    public void vectorSearchMethods() {}

    /**
     * 环绕通知：记录向量检索指标
     */
    @Around("vectorSearchMethods()")
    public Object recordVectorSearchMetrics(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        
        Object result = null;
        try {
            result = joinPoint.proceed();
            return result;
        } finally {
            long queryTime = System.currentTimeMillis() - startTime;
            
            // 提取结果信息
            int numResults = 0;
            Float topScore = null;
            
            if (result instanceof List) {
                @SuppressWarnings("unchecked")
                List<SearchResult> searchResults = (List<SearchResult>) result;
                numResults = searchResults.size();
                
                if (!searchResults.isEmpty()) {
                    topScore = searchResults.get(0).getScore();
                }
            }
            
            // 记录指标
            vectorSearchMetrics.recordSearch(queryTime, numResults, topScore);
        }
    }
}
