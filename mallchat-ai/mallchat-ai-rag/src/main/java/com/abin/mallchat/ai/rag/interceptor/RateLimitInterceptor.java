package com.abin.mallchat.ai.rag.interceptor;

import com.abin.mallchat.ai.common.exception.AIErrorEnum;
import com.abin.mallchat.ai.common.exception.AIException;
import com.abin.mallchat.ai.rag.service.RateLimitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * AI模块限流拦截器
 * 
 * @author zxw
 */
@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    
    @Autowired
    private RateLimitService rateLimitService;
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String uri = request.getRequestURI();
        
        // 从请求中获取用户ID（实际项目中应该从token或session中获取）
        Long userId = getUserIdFromRequest(request);
        if (userId == null) {
            // 未登录用户不进行限流检查
            return true;
        }
        
        // 根据不同的接口应用不同的限流策略
        if (uri.contains("/api/ai/rag/query")) {
            if (!rateLimitService.checkRagQueryLimit(userId)) {
                throw new AIException(AIErrorEnum.LLM_RATE_LIMIT);
            }
        } else if (uri.contains("/api/ai/documents/upload")) {
            if (!rateLimitService.checkDocumentUploadLimit(userId)) {
                throw new AIException(AIErrorEnum.LLM_RATE_LIMIT);
            }
        } else if (uri.contains("/api/ai/question")) {
            if (!rateLimitService.checkQuestionLimit(userId)) {
                throw new AIException(AIErrorEnum.LLM_RATE_LIMIT);
            }
        }
        
        return true;
    }
    
    /**
     * 从请求中获取用户ID
     * 实际项目中应该从token或session中获取
     */
    private Long getUserIdFromRequest(HttpServletRequest request) {
        // 从请求头中获取用户ID
        String userIdHeader = request.getHeader("X-User-Id");
        if (userIdHeader != null) {
            try {
                return Long.parseLong(userIdHeader);
            } catch (NumberFormatException e) {
                log.warn("Invalid user ID in header: {}", userIdHeader);
            }
        }
        
        // 从请求参数中获取用户ID
        String userIdParam = request.getParameter("userId");
        if (userIdParam != null) {
            try {
                return Long.parseLong(userIdParam);
            } catch (NumberFormatException e) {
                log.warn("Invalid user ID in parameter: {}", userIdParam);
            }
        }
        
        return null;
    }
}
