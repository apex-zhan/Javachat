package com.abin.mallchat.ai.rag.service;

/**
 * AI模块限流服务接口
 * 
 * @author zxw
 */
public interface RateLimitService {
    
    /**
     * 检查RAG查询是否超过限流
     * 
     * @param userId 用户ID
     * @return true表示允许请求，false表示超过限流
     */
    boolean checkRagQueryLimit(Long userId);
    
    /**
     * 检查文档上传是否超过限流
     * 
     * @param userId 用户ID
     * @return true表示允许请求，false表示超过限流
     */
    boolean checkDocumentUploadLimit(Long userId);
    
    /**
     * 检查智能问答是否超过限流
     * 
     * @param userId 用户ID
     * @return true表示允许请求，false表示超过限流
     */
    boolean checkQuestionLimit(Long userId);
    
    /**
     * 获取用户剩余配额
     * 
     * @param userId 用户ID
     * @param limitType 限流类型
     * @return 剩余配额
     */
    int getRemainingQuota(Long userId, String limitType);
}
