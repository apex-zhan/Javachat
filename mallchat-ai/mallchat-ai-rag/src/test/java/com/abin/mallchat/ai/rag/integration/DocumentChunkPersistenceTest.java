package com.abin.mallchat.ai.rag.integration;

import com.abin.mallchat.ai.common.dao.DocumentChunkDao;
import com.abin.mallchat.ai.common.domain.entity.DocumentChunk;
import com.abin.mallchat.ai.rag.domain.dto.ChunkMetadata;
import com.abin.mallchat.ai.rag.aspect.ChunkStrategy;
import com.abin.mallchat.ai.rag.service.DocumentProcessingService;
import com.abin.mallchat.ai.rag.service.impl.TikaDocumentProcessingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文档分块持久化集成测试
 * 验证分块元数据管理和数据库保存功能
 */
@SpringBootTest
@Transactional
class DocumentChunkPersistenceTest {
    
    @Autowired
    private DocumentProcessingService documentProcessingService;
    
    @Autowired
    private DocumentChunkDao documentChunkDao;
    
    @Test
    void testCompleteChunkWorkflow() {
        // Given: 准备测试数据
        String content = "This is a comprehensive test document. ".repeat(100);
        Long documentId = 999L;
        String documentTitle = "Integration Test Document";
        String documentType = "txt";
        
        // When: 执行完整的分块流程
        // 1. 分块
        List<DocumentChunk> chunks = documentProcessingService.chunkDocument(content, ChunkStrategy.FIXED_SIZE);
        assertThat(chunks).isNotEmpty();
        
        // 2. 设置文档元数据
        if (documentProcessingService instanceof TikaDocumentProcessingService) {
            TikaDocumentProcessingService service = (TikaDocumentProcessingService) documentProcessingService;
            service.setDocumentMetadata(chunks, documentId, documentTitle, documentType);
        }
        
        // 3. 保存到数据库
        boolean saved = documentChunkDao.saveBatch(chunks);
        assertThat(saved).isTrue();
        
        // Then: 验证数据库中的数据
        List<DocumentChunk> savedChunks = documentChunkDao.listByDocumentId(documentId);
        assertThat(savedChunks).hasSize(chunks.size());
        
        // 验证每个分块的元数据
        for (int i = 0; i < savedChunks.size(); i++) {
            DocumentChunk savedChunk = savedChunks.get(i);
            
            // 验证基本字段
            assertThat(savedChunk.getDocumentId()).isEqualTo(documentId);
            assertThat(savedChunk.getChunkIndex()).isEqualTo(i);
            assertThat(savedChunk.getContent()).isNotEmpty();
            assertThat(savedChunk.getTokenCount()).isGreaterThan(0);
            
            // 验证元数据
            assertThat(savedChunk.getMetadata()).isNotNull();
            ChunkMetadata metadata = ChunkMetadata.fromJson(savedChunk.getMetadata());
            
            assertThat(metadata.getDocumentId()).isEqualTo(documentId);
            assertThat(metadata.getChunkIndex()).isEqualTo(i);
            assertThat(metadata.getDocumentTitle()).isEqualTo(documentTitle);
            assertThat(metadata.getDocumentType()).isEqualTo(documentType);
            assertThat(metadata.getSourceLocation()).isNotNull();
            assertThat(metadata.getSourceLocationEnd()).isNotNull();
            assertThat(metadata.getChunkStrategy()).isEqualTo("FIXED_SIZE");
        }
        
        // 验证统计功能
        long count = documentChunkDao.countByDocumentId(documentId);
        assertThat(count).isEqualTo(chunks.size());
    }
    
    @Test
    void testDeleteChunksByDocumentId() {
        // Given: 创建并保存分块
        String content = "Test content for deletion. ".repeat(50);
        Long documentId = 888L;
        
        List<DocumentChunk> chunks = documentProcessingService.chunkDocument(content, ChunkStrategy.FIXED_SIZE);
        
        if (documentProcessingService instanceof TikaDocumentProcessingService) {
            TikaDocumentProcessingService service = (TikaDocumentProcessingService) documentProcessingService;
            service.setDocumentMetadata(chunks, documentId, "Test", "txt");
        }
        
        documentChunkDao.saveBatch(chunks);
        
        // 验证保存成功
        long countBefore = documentChunkDao.countByDocumentId(documentId);
        assertThat(countBefore).isGreaterThan(0);
        
        // When: 删除分块
        boolean deleted = documentChunkDao.deleteByDocumentId(documentId);
        assertThat(deleted).isTrue();
        
        // Then: 验证删除成功
        long countAfter = documentChunkDao.countByDocumentId(documentId);
        assertThat(countAfter).isEqualTo(0);
    }
}
