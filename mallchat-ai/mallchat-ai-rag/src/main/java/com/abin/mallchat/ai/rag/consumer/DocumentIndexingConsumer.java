package com.abin.mallchat.ai.rag.consumer;

import com.abin.mallchat.ai.common.dao.DocumentChunkDao;
import com.abin.mallchat.ai.common.dao.KnowledgeDocumentDao;
import com.abin.mallchat.ai.common.domain.entity.DocumentChunk;
import com.abin.mallchat.ai.common.domain.entity.KnowledgeDocument;
import com.abin.mallchat.ai.common.domain.enums.IndexStatus;
import com.abin.mallchat.ai.rag.domain.dto.ChunkMetadata;
import com.abin.mallchat.ai.rag.domain.dto.DocumentIndexingMessage;
import com.abin.mallchat.ai.rag.cache.DocumentMetadataCache;
import com.abin.mallchat.ai.rag.cache.IndexStatusCache;
import com.abin.mallchat.ai.rag.aspect.BatchProcessingService;
import com.abin.mallchat.ai.rag.aspect.ChunkStrategy;
import com.abin.mallchat.ai.rag.service.DocumentProcessingService;
import com.abin.mallchat.ai.vector.service.VectorService;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档索引消息消费者
 * 处理文档的解析、分块、向量生成和存储
 * 
 * @author zxw
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = "${rocketmq.ai.document-indexing-topic:MALLCHAT_AI_DOCUMENT_INDEXING}",
        consumerGroup = "${rocketmq.ai.document-indexing-consumer-group:mallchat-ai-indexing-consumer}"
)
public class DocumentIndexingConsumer implements RocketMQListener<DocumentIndexingMessage> {
    
    @Autowired
    private KnowledgeDocumentDao knowledgeDocumentDao;
    
    @Autowired
    private DocumentChunkDao documentChunkDao;
    
    @Autowired
    private DocumentProcessingService documentProcessingService;
    
    @Autowired
    private VectorService vectorService;
    
    @Autowired
    private DocumentMetadataCache documentMetadataCache;
    
    @Autowired
    private IndexStatusCache indexStatusCache;
    
    @Autowired
    private BatchProcessingService batchProcessingService;
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onMessage(DocumentIndexingMessage message) {
        log.info("收到文档索引任务，文档ID：{}, 标题：{}", message.getDocumentId(), message.getTitle());
        
        Long documentId = message.getDocumentId();
        
        try {
            // 1. 更新文档状态为INDEXING
            updateDocumentStatus(documentId, IndexStatus.INDEXING, null);
            
            // 2. 解析文档内容
            log.info("开始解析文档，文档ID：{}", documentId);
            File documentFile = new File(message.getFilePath());
            String content = documentProcessingService.parseDocument(documentFile);
            
            // 3. 文档分块
            log.info("开始分块文档，文档ID：{}", documentId);
            ChunkStrategy strategy = determineChunkStrategy(message.getDocumentType());
            List<ChunkMetadata> chunks = documentProcessingService.chunkDocument(content, strategy);
            log.info("文档分块完成，文档ID：{}, 分块数量：{}", documentId, chunks.size());
            
            // 4. 生成向量（使用批量处理优化）
            log.info("开始生成向量，文档ID：{}", documentId);
            List<String> chunkContents = new ArrayList<>();
            for (ChunkMetadata chunk : chunks) {
                chunkContents.add(chunk.getContent());
            }
            
            // 使用批量处理服务生成向量，自动分批和优化
            List<float[]> embeddings = batchProcessingService.batchGenerateEmbeddings(chunkContents);
            log.info("向量生成完成，文档ID：{}, 向量数量：{}", documentId, embeddings.size());
            
            // 5. 保存分块和向量
            log.info("开始保存分块和向量，文档ID：{}", documentId);
            List<DocumentChunk> documentChunks = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                ChunkMetadata chunkMetadata = chunks.get(i);
                
                DocumentChunk documentChunk = new DocumentChunk();
                documentChunk.setDocumentId(documentId);
                documentChunk.setChunkIndex(i);
                documentChunk.setContent(chunkMetadata.getContent());
                documentChunk.setTokenCount(chunkMetadata.getTokenCount());
                documentChunk.setMetadata(JSON.toJSONString(chunkMetadata.getMetadata()));
                documentChunk.setCreateTime(LocalDateTime.now());
                
                // 暂时不设置vectorId，等向量存储后再更新
                documentChunks.add(documentChunk);
            }
            
            // 批量保存分块到数据库
            documentChunkDao.saveBatch(documentChunks);
            
            // 6. 存储向量到向量数据库
            vectorService.storeVectors(documentId, documentChunks);
            log.info("向量存储完成，文档ID：{}", documentId);
            
            // 7. 更新文档状态为COMPLETED
            updateDocumentStatus(documentId, IndexStatus.COMPLETED, null);
            updateDocumentChunkCount(documentId, chunks.size());
            
            // 8. 更新缓存
            documentMetadataCache.invalidateDocumentMetadata(documentId);
            indexStatusCache.updateIndexStatus(documentId, IndexStatus.COMPLETED.name());
            
            log.info("文档索引任务完成，文档ID：{}", documentId);
            
        } catch (Exception e) {
            log.error("文档索引任务失败，文档ID：{}", documentId, e);
            
            // 更新文档状态为FAILED
            updateDocumentStatus(documentId, IndexStatus.FAILED, e.getMessage());
            
            // 更新缓存
            indexStatusCache.updateIndexStatus(documentId, IndexStatus.FAILED.name());
            
            // 如果重试次数未超过限制，可以考虑重新发送消息
            if (message.getRetryCount() < 3) {
                log.info("准备重试文档索引任务，文档ID：{}, 重试次数：{}", 
                        documentId, message.getRetryCount() + 1);
                // TODO: 实现重试逻辑
            }
        }
    }
    
    /**
     * 更新文档状态
     */
    private void updateDocumentStatus(Long documentId, IndexStatus status, String errorMessage) {
        KnowledgeDocument document = knowledgeDocumentDao.getById(documentId);
        if (document != null) {
            document.setIndexStatus(status.name());
            document.setErrorMessage(errorMessage);
            document.setUpdateTime(LocalDateTime.now());
            knowledgeDocumentDao.updateById(document);
        }
    }
    
    /**
     * 更新文档分块数量
     */
    private void updateDocumentChunkCount(Long documentId, int chunkCount) {
        KnowledgeDocument document = knowledgeDocumentDao.getById(documentId);
        if (document != null) {
            document.setChunkCount(chunkCount);
            document.setUpdateTime(LocalDateTime.now());
            knowledgeDocumentDao.updateById(document);
        }
    }
    
    /**
     * 根据文档类型确定分块策略
     */
    private ChunkStrategy determineChunkStrategy(String documentType) {
        if (documentType == null) {
            return ChunkStrategy.FIXED_SIZE;
        }
        
        switch (documentType.toLowerCase()) {
            case "md":
            case "html":
                return ChunkStrategy.SEMANTIC;
            case "txt":
            case "pdf":
            case "docx":
            case "doc":
            default:
                return ChunkStrategy.FIXED_SIZE;
        }
    }
}
