package com.abin.mallchat.ai.vector.service;

import com.abin.mallchat.ai.common.domain.entity.DocumentChunk;
import com.abin.mallchat.ai.vector.domain.SearchResult;

import java.util.List;

/**
 * 向量服务接口
 * 负责向量的存储、检索和删除操作
 * 
 * @author abin
 */
public interface VectorService {
    
    /**
     * 存储文档的向量数据
     * 
     * @param documentId 文档 ID
     * @param chunks 文档分块列表（包含内容和向量）
     */
    void storeVectors(Long documentId, List<DocumentChunk> chunks);
    
    /**
     * 相似度检索
     * 
     * @param queryVector 查询向量
     * @param topK 返回数量
     * @param documentId 限定文档 ID（可选，null 表示全局检索）
     * @return 检索结果列表，按相似度降序排列
     */
    List<SearchResult> search(float[] queryVector, int topK, Long documentId);
    
    /**
     * 删除文档的所有向量（幂等操作）
     * 如果向量不存在，也返回成功
     * 
     * @param documentId 文档 ID
     */
    void deleteVectors(Long documentId);
    
    /**
     * 检查文档的向量是否存在
     * 
     * @param documentId 文档 ID
     * @return true 如果存在，false 如果不存在
     */
    boolean exists(Long documentId);
}
