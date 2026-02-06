package com.abin.mallchat.ai.rag.service.impl;

import com.abin.mallchat.ai.common.domain.entity.DocumentChunk;
import com.abin.mallchat.ai.rag.domain.dto.ChunkMetadata;
import com.abin.mallchat.ai.rag.aspect.ChunkStrategy;
import com.abin.mallchat.ai.rag.service.DocumentProcessingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分块元数据管理测试
 * 验证 Requirements 5.5: 分块元数据完整性
 */
@SpringBootTest
class ChunkMetadataTest {
    
    @Autowired
    private DocumentProcessingService documentProcessingService;
    
    @Test
    void testChunkMetadataCompleteness() {
        // Given: 一段测试文本
        String content = "This is a test document. ".repeat(50); // 创建足够长的文本以生成多个分块
        
        // When: 执行分块
        List<DocumentChunk> chunks = documentProcessingService.chunkDocument(content, ChunkStrategy.FIXED_SIZE);
        
        // Then: 验证每个分块都有元数据
        assertThat(chunks).isNotEmpty();
        
        for (DocumentChunk chunk : chunks) {
            // 验证基本字段
            assertThat(chunk.getChunkIndex()).isNotNull();
            assertThat(chunk.getContent()).isNotEmpty();
            assertThat(chunk.getTokenCount()).isGreaterThan(0);
            assertThat(chunk.getCreateTime()).isNotNull();
            
            // 验证元数据存在
            assertThat(chunk.getMetadata()).isNotNull();
            
            // 解析并验证元数据内容
            ChunkMetadata metadata = ChunkMetadata.fromJson(chunk.getMetadata());
            assertThat(metadata.getChunkIndex()).isEqualTo(chunk.getChunkIndex());
            assertThat(metadata.getSourceLocation()).isNotNull();
            assertThat(metadata.getSourceLocationEnd()).isNotNull();
            assertThat(metadata.getChunkStrategy()).isEqualTo("FIXED_SIZE");
            assertThat(metadata.getCreateTimestamp()).isNotNull();
            
            // 验证来源位置的合理性
            assertThat(metadata.getSourceLocation()).isGreaterThanOrEqualTo(0);
            assertThat(metadata.getSourceLocationEnd()).isGreaterThan(metadata.getSourceLocation());
        }
    }
    
    @Test
    void testSetDocumentMetadata() {
        // Given: 创建分块
        String content = "Test content for metadata. ".repeat(30);
        List<DocumentChunk> chunks = documentProcessingService.chunkDocument(content, ChunkStrategy.FIXED_SIZE);
        
        // When: 设置文档级别元数据
        Long documentId = 12345L;
        String documentTitle = "Test Document";
        String documentType = "txt";
        
        if (documentProcessingService instanceof TikaDocumentProcessingService) {
            TikaDocumentProcessingService service = (TikaDocumentProcessingService) documentProcessingService;
            service.setDocumentMetadata(chunks, documentId, documentTitle, documentType);
        }
        
        // Then: 验证文档级别元数据已设置
        for (DocumentChunk chunk : chunks) {
            assertThat(chunk.getDocumentId()).isEqualTo(documentId);
            
            ChunkMetadata metadata = ChunkMetadata.fromJson(chunk.getMetadata());
            assertThat(metadata.getDocumentId()).isEqualTo(documentId);
            assertThat(metadata.getDocumentTitle()).isEqualTo(documentTitle);
            assertThat(metadata.getDocumentType()).isEqualTo(documentType);
        }
    }
    
    @Test
    void testSemanticChunkingMetadata() {
        // Given: 包含段落的文本
        String content = "First paragraph.\n\nSecond paragraph.\n\nThird paragraph.";
        
        // When: 使用语义分块
        List<DocumentChunk> chunks = documentProcessingService.chunkDocument(content, ChunkStrategy.SEMANTIC);
        
        // Then: 验证元数据策略正确
        assertThat(chunks).isNotEmpty();
        
        for (DocumentChunk chunk : chunks) {
            ChunkMetadata metadata = ChunkMetadata.fromJson(chunk.getMetadata());
            assertThat(metadata.getChunkStrategy()).isEqualTo("SEMANTIC");
            assertThat(metadata.getSourceLocation()).isNotNull();
            assertThat(metadata.getSourceLocationEnd()).isNotNull();
        }
    }
    
    @Test
    void testMetadataJsonSerialization() {
        // Given: 创建元数据对象
        ChunkMetadata metadata = ChunkMetadata.builder()
                .documentId(123L)
                .chunkIndex(0)
                .sourceLocation(0)
                .sourceLocationEnd(100)
                .documentTitle("Test")
                .documentType("txt")
                .chunkStrategy("FIXED_SIZE")
                .createTimestamp(System.currentTimeMillis())
                .build();
        
        // When: 序列化和反序列化
        String json = metadata.toJson();
        ChunkMetadata deserialized = ChunkMetadata.fromJson(json);
        
        // Then: 验证数据一致性
        assertThat(deserialized.getDocumentId()).isEqualTo(metadata.getDocumentId());
        assertThat(deserialized.getChunkIndex()).isEqualTo(metadata.getChunkIndex());
        assertThat(deserialized.getSourceLocation()).isEqualTo(metadata.getSourceLocation());
        assertThat(deserialized.getSourceLocationEnd()).isEqualTo(metadata.getSourceLocationEnd());
        assertThat(deserialized.getDocumentTitle()).isEqualTo(metadata.getDocumentTitle());
        assertThat(deserialized.getDocumentType()).isEqualTo(metadata.getDocumentType());
        assertThat(deserialized.getChunkStrategy()).isEqualTo(metadata.getChunkStrategy());
        assertThat(deserialized.getCreateTimestamp()).isEqualTo(metadata.getCreateTimestamp());
    }
}
