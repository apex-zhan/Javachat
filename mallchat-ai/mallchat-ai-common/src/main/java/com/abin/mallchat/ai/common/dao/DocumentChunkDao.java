package com.abin.mallchat.ai.common.dao;

import com.abin.mallchat.ai.common.domain.entity.DocumentChunk;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文档分块DAO
 * 
 * @author zxw
 */
@Service
public class DocumentChunkDao extends ServiceImpl<DocumentChunkMapper, DocumentChunk> {
    
    /**
     * 批量保存文档分块
     * 
     * @param chunks 分块列表
     * @return 是否成功
     */
    public boolean saveBatch(List<DocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return true;
        }
        return super.saveBatch(chunks);
    }
    
    /**
     * 根据文档ID查询所有分块
     * 
     * @param documentId 文档ID
     * @return 分块列表
     */
    public List<DocumentChunk> listByDocumentId(Long documentId) {
        return lambdaQuery()
                .eq(DocumentChunk::getDocumentId, documentId)
                .orderByAsc(DocumentChunk::getChunkIndex)
                .list();
    }
    
    /**
     * 根据文档ID删除所有分块
     * 
     * @param documentId 文档ID
     * @return 是否成功
     */
    public boolean deleteByDocumentId(Long documentId) {
        return lambdaUpdate()
                .eq(DocumentChunk::getDocumentId, documentId)
                .remove();
    }
    
    /**
     * 根据文档ID统计分块数量
     * 
     * @param documentId 文档ID
     * @return 分块数量
     */
    public long countByDocumentId(Long documentId) {
        return lambdaQuery()
                .eq(DocumentChunk::getDocumentId, documentId)
                .count();
    }
}
