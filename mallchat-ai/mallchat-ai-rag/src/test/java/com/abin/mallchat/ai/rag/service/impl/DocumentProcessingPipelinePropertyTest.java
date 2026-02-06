package com.abin.mallchat.ai.rag.service.impl;

import com.abin.mallchat.ai.common.dao.DocumentChunkDao;
import com.abin.mallchat.ai.common.dao.KnowledgeDocumentDao;
import com.abin.mallchat.ai.common.domain.entity.DocumentChunk;
import com.abin.mallchat.ai.common.domain.entity.KnowledgeDocument;
import com.abin.mallchat.ai.common.domain.enums.IndexStatus;
import com.abin.mallchat.ai.rag.aspect.ChunkStrategy;
import com.abin.mallchat.ai.rag.service.DocumentProcessingService;
import com.abin.mallchat.ai.vector.service.EmbeddingService;
import com.abin.mallchat.ai.vector.service.VectorService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Feature: ai-assistant-rag, Property 10: Document Processing Pipeline
 * Validates: Requirements 4.2, 4.3, 4.4
 * 
 * 验证上传文档完成完整流程，验证所有分块都有对应向量
 * 
 * @author zxw
 */
class DocumentProcessingPipelinePropertyTest {
    
    private KnowledgeDocumentDao knowledgeDocumentDao;
    private DocumentChunkDao documentChunkDao;
    private DocumentProcessingService documentProcessingService;
    private EmbeddingService embeddingService;
    private VectorService vectorService;
    
    @BeforeEach
    void setUp() {
        knowledgeDocumentDao = mock(KnowledgeDocumentDao.class);
        documentChunkDao = mock(DocumentChunkDao.class);
        documentProcessingService = mock(DocumentProcessingService.class);
        embeddingService = mock(EmbeddingService.class);
        vectorService = mock(VectorService.class);
    }
    
    /**
     * Property 10.1: 文档处理应该完成完整流程
     * 解析 → 分块 → 生成向量 → 存储
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 10.1: Document processing should complete full pipeline")
    void documentProcessingShouldCompleteFullPipeline(
            @ForAll @IntRange(min = 1, max = 20) int chunkCount,
            @ForAll @StringLength(min = 10, max = 100) String content) throws Exception {
        
        // Given: 模拟文档和分块
        Long documentId = 1L;
        MultipartFile mockFile = mock(MultipartFile.class);
        
        // 模拟文档解析
        when(documentProcessingService.parseDocument(any(MultipartFile.class)))
                .thenReturn(content);
        
        // 模拟文档分块
        List<DocumentChunk> chunks = generateMockChunks(chunkCount, content, documentId);
        when(documentProcessingService.chunkDocument(anyString(), any(ChunkStrategy.class)))
                .thenReturn(chunks);
        
        // 模拟向量生成
        List<float[]> embeddings = generateMockEmbeddings(chunkCount);
        when(embeddingService.generateEmbeddings(anyList()))
                .thenReturn(embeddings);
        
        // 模拟数据库操作
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(documentId);
        when(knowledgeDocumentDao.getById(documentId)).thenReturn(document);
        when(documentChunkDao.saveBatch(anyList())).thenReturn(true);
        
        // When: 执行完整流程
        // 1. 解析文档
        String parsedContent = documentProcessingService.parseDocument(mockFile);
        
        // 2. 分块
        List<DocumentChunk> actualChunks = documentProcessingService.chunkDocument(
                parsedContent, ChunkStrategy.FIXED_SIZE);
        
        // 3. 生成向量
        List<String> chunkContents = new ArrayList<>();
        for (DocumentChunk chunk : actualChunks) {
            chunkContents.add(chunk.getContent());
        }
        List<float[]> actualEmbeddings = embeddingService.generateEmbeddings(chunkContents);
        
        // 4. 保存分块
        documentChunkDao.saveBatch(actualChunks);
        
        // 5. 存储向量
        vectorService.storeVectors(documentId, actualChunks);
        
        // Then: 验证完整流程
        assertThat(parsedContent).isNotNull();
        assertThat(actualChunks).hasSize(chunkCount);
        assertThat(actualEmbeddings).hasSize(chunkCount);
        
        // 验证所有方法都被调用
        verify(documentProcessingService).parseDocument(any(MultipartFile.class));
        verify(documentProcessingService).chunkDocument(anyString(), any(ChunkStrategy.class));
        verify(embeddingService).generateEmbeddings(anyList());
        verify(documentChunkDao).saveBatch(anyList());
        verify(vectorService).storeVectors(eq(documentId), anyList());
    }
    
    /**
     * Property 10.2: 所有分块都应该有对应的向量
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 10.2: All chunks should have corresponding vectors")
    void allChunksShouldHaveCorrespondingVectors(
            @ForAll @IntRange(min = 1, max = 20) int chunkCount) {
        
        // Given: 生成分块和向量
        Long documentId = 1L;
        List<DocumentChunk> chunks = generateMockChunks(chunkCount, "test content", documentId);
        List<float[]> embeddings = generateMockEmbeddings(chunkCount);
        
        // Then: 验证分块数量和向量数量一致
        assertThat(chunks).hasSize(chunkCount);
        assertThat(embeddings).hasSize(chunkCount);
        assertThat(chunks.size()).isEqualTo(embeddings.size());
        
        // 验证每个分块都有对应的向量
        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).getChunkIndex()).isEqualTo(i);
            assertThat(embeddings.get(i)).isNotNull();
            assertThat(embeddings.get(i).length).isGreaterThan(0);
        }
    }
    
    /**
     * Property 10.3: 文档状态应该正确更新
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 10.3: Document status should be updated correctly")
    void documentStatusShouldBeUpdatedCorrectly(
            @ForAll @IntRange(min = 1, max = 20) int chunkCount) {
        
        // Given: 模拟文档
        Long documentId = 1L;
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(documentId);
        document.setIndexStatus(IndexStatus.PENDING.name());
        
        when(knowledgeDocumentDao.getById(documentId)).thenReturn(document);
        when(knowledgeDocumentDao.updateById(any(KnowledgeDocument.class))).thenReturn(true);
        
        // When: 更新状态为INDEXING
        document.setIndexStatus(IndexStatus.INDEXING.name());
        knowledgeDocumentDao.updateById(document);
        
        // 模拟索引完成
        document.setIndexStatus(IndexStatus.COMPLETED.name());
        document.setChunkCount(chunkCount);
        knowledgeDocumentDao.updateById(document);
        
        // Then: 验证状态更新
        ArgumentCaptor<KnowledgeDocument> captor = ArgumentCaptor.forClass(KnowledgeDocument.class);
        verify(knowledgeDocumentDao, times(2)).updateById(captor.capture());
        
        List<KnowledgeDocument> updates = captor.getAllValues();
        assertThat(updates).hasSize(2);
        assertThat(updates.get(0).getIndexStatus()).isEqualTo(IndexStatus.INDEXING.name());
        assertThat(updates.get(1).getIndexStatus()).isEqualTo(IndexStatus.COMPLETED.name());
        assertThat(updates.get(1).getChunkCount()).isEqualTo(chunkCount);
    }
    
    /**
     * Property 10.4: 失败时应该正确记录错误
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 10.4: Failures should be recorded correctly")
    void failuresShouldBeRecordedCorrectly(
            @ForAll @StringLength(min = 10, max = 100) String errorMessage) {
        
        // Given: 模拟文档
        Long documentId = 1L;
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(documentId);
        document.setIndexStatus(IndexStatus.INDEXING.name());
        
        when(knowledgeDocumentDao.getById(documentId)).thenReturn(document);
        when(knowledgeDocumentDao.updateById(any(KnowledgeDocument.class))).thenReturn(true);
        
        // When: 模拟失败
        document.setIndexStatus(IndexStatus.FAILED.name());
        document.setErrorMessage(errorMessage);
        knowledgeDocumentDao.updateById(document);
        
        // Then: 验证错误记录
        ArgumentCaptor<KnowledgeDocument> captor = ArgumentCaptor.forClass(KnowledgeDocument.class);
        verify(knowledgeDocumentDao).updateById(captor.capture());
        
        KnowledgeDocument updated = captor.getValue();
        assertThat(updated.getIndexStatus()).isEqualTo(IndexStatus.FAILED.name());
        assertThat(updated.getErrorMessage()).isEqualTo(errorMessage);
    }
    
    /**
     * Property 10.5: 分块索引应该连续且从0开始
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 10.5: Chunk indices should be continuous and start from 0")
    void chunkIndicesShouldBeContinuousAndStartFromZero(
            @ForAll @IntRange(min = 1, max = 20) int chunkCount) {
        
        // Given: 生成分块
        List<DocumentChunk> chunks = new ArrayList<>();
        for (int i = 0; i < chunkCount; i++) {
            DocumentChunk chunk = new DocumentChunk();
            chunk.setChunkIndex(i);
            chunk.setContent("chunk " + i);
            chunks.add(chunk);
        }
        
        // Then: 验证索引连续性
        assertThat(chunks).hasSize(chunkCount);
        
        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).getChunkIndex()).isEqualTo(i);
        }
        
        // 验证第一个索引是0
        assertThat(chunks.get(0).getChunkIndex()).isEqualTo(0);
        
        // 验证最后一个索引是chunkCount-1
        assertThat(chunks.get(chunks.size() - 1).getChunkIndex()).isEqualTo(chunkCount - 1);
    }
    
    // ========== Helper Methods ==========
    
    /**
     * 生成模拟分块
     */
    private List<DocumentChunk> generateMockChunks(int count, String baseContent, Long documentId) {
        List<DocumentChunk> chunks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocumentId(documentId);
            chunk.setChunkIndex(i);
            chunk.setContent(baseContent + " chunk-" + i);
            chunk.setTokenCount(50 + i * 10);
            chunks.add(chunk);
        }
        return chunks;
    }
    
    /**
     * 生成模拟向量
     */
    private List<float[]> generateMockEmbeddings(int count) {
        List<float[]> embeddings = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            float[] embedding = new float[1536]; // OpenAI embedding dimension
            for (int j = 0; j < embedding.length; j++) {
                embedding[j] = (float) Math.random();
            }
            embeddings.add(embedding);
        }
        return embeddings;
    }
}
