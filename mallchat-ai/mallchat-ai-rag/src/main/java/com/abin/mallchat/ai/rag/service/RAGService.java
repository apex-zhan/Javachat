package com.abin.mallchat.ai.rag.service;

import com.abin.mallchat.ai.rag.domain.dto.DocumentUpdateRequest;
import com.abin.mallchat.ai.rag.domain.dto.DocumentUploadRequest;
import com.abin.mallchat.ai.rag.domain.dto.DocumentUploadResponse;
import com.abin.mallchat.ai.rag.domain.dto.RAGQueryRequest;
import reactor.core.publisher.Flux;

/**
 * RAG服务接口
 * 协调RAG问答的完整流程
 * 
 * @author zxw
 */
public interface RAGService {
    
    /**
     * RAG 问答（流式）
     * 
     * @param request RAG查询请求
     * @return 流式响应
     */
    Flux<String> ragQuery(RAGQueryRequest request);
    
    /**
     * 上传并索引知识文档
     * 
     * @param request 文档上传请求
     * @return 文档上传响应
     */
    DocumentUploadResponse uploadDocument(DocumentUploadRequest request);
    
    /**
     * 更新知识文档（幂等删除旧版本）
     * 
     * @param documentId 文档 ID
     * @param request 文档更新请求
     * @return 文档上传响应
     */
    DocumentUploadResponse updateDocument(Long documentId, DocumentUpdateRequest request);
    
    /**
     * 删除知识文档（幂等）
     * 
     * @param documentId 文档 ID
     */
    void deleteDocument(Long documentId);
    
    /**
     * 检查索引状态
     * 
     * @param documentId 文档 ID
     * @return 索引状态
     */
    String checkIndexStatus(Long documentId);
}
