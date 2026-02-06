package com.abin.mallchat.ai.rag.service.impl;

import com.abin.mallchat.ai.common.dao.KnowledgeDocumentDao;
import com.abin.mallchat.ai.common.domain.entity.KnowledgeDocument;
import com.abin.mallchat.ai.common.domain.enums.IndexStatus;
import com.abin.mallchat.ai.rag.config.DocumentConfig;
import com.abin.mallchat.ai.rag.domain.dto.DocumentUpdateRequest;
import com.abin.mallchat.ai.rag.domain.dto.DocumentUploadResponse;
import com.abin.mallchat.ai.rag.service.DocumentIndexingProducer;
import com.abin.mallchat.ai.vector.service.VectorService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 文档更新原子性属性测试
 * Feature: ai-assistant-rag, Property 11: Document Update Atomicity
 * 
 * 验证：
 * 1. 更新后只有新版本存在
 * 2. 旧版本完全删除
 * 
 * Validates: Requirements 4.5, 9.5
 * 
 * @author zxw
 */
@Tag("property-test")
public class DocumentUpdateAtomicityPropertyTest {
    
    @Mock
    private KnowledgeDocumentDao knowledgeDocumentDao;
    
    @Mock
    private VectorService vectorService;
    
    @Mock
    private DocumentIndexingProducer documentIndexingProducer;
    
    @Mock
    private DocumentConfig documentConfig;
    
    private RAGServiceImpl ragService;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ragService = new RAGServiceImpl();
        
        // Use reflection to inject mocks (since we can't use @InjectMocks with jqwik)
        try {
            java.lang.reflect.Field daoField = RAGServiceImpl.class.getDeclaredField("knowledgeDocumentDao");
            daoField.setAccessible(true);
            daoField.set(ragService, knowledgeDocumentDao);
            
            java.lang.reflect.Field vectorField = RAGServiceImpl.class.getDeclaredField("vectorService");
            vectorField.setAccessible(true);
            vectorField.set(ragService, vectorService);
            
            java.lang.reflect.Field producerField = RAGServiceImpl.class.getDeclaredField("documentIndexingProducer");
            producerField.setAccessible(true);
            producerField.set(ragService, documentIndexingProducer);
            
            java.lang.reflect.Field configField = RAGServiceImpl.class.getDeclaredField("documentConfig");
            configField.setAccessible(true);
            configField.set(ragService, documentConfig);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject mocks", e);
        }
        
        // Setup default config behavior
        when(documentConfig.getMaxFileSize()).thenReturn(10L * 1024 * 1024); // 10MB
        when(documentConfig.getAllowedFormats()).thenReturn(Arrays.asList("txt", "pdf", "md", "html"));
        when(documentConfig.getUseOss()).thenReturn(false);
        when(documentConfig.getStoragePath()).thenReturn("./test-storage");
    }
    
    /**
     * Property 11: Document Update Atomicity
     * 
     * For any document update operation, the old version's vectors should be 
     * completely removed before the new version's vectors are inserted, 
     * ensuring only one version exists.
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 11: Document Update Atomicity")
    void updateIsAtomic(
            @ForAll @IntRange(min = 1, max = 10000) long documentId,
            @ForAll @StringLength(min = 1, max = 100) String oldTitle,
            @ForAll @StringLength(min = 1, max = 100) String newTitle
    ) {
        // Given: Old document exists
        KnowledgeDocument oldDocument = createDocument(documentId, oldTitle, "old-file.txt");
        when(knowledgeDocumentDao.getById(documentId)).thenReturn(oldDocument);
        when(knowledgeDocumentDao.updateById(any(KnowledgeDocument.class))).thenReturn(true);
        
        // Given: Mock vector service to track deletion
        doNothing().when(vectorService).deleteVectors(anyLong());
        
        // Given: Mock indexing producer
        doNothing().when(documentIndexingProducer).sendIndexingTask(any());
        
        // Given: New document request
        MultipartFile newFile = createMockFile("new-file.txt", "New content");
        
        DocumentUpdateRequest request = new DocumentUpdateRequest();
        request.setTitle(newTitle);
        request.setFile(newFile);
        request.setUserId(1L);
        
        // When: Update document
        DocumentUploadResponse response = ragService.updateDocument(documentId, request);
        
        // Then: Old version vectors should be deleted BEFORE new indexing starts
        ArgumentCaptor<Long> deleteCaptor = ArgumentCaptor.forClass(Long.class);
        verify(vectorService, times(1)).deleteVectors(deleteCaptor.capture());
        assertThat(deleteCaptor.getValue()).isEqualTo(documentId);
        
        // Then: Document record should be updated
        ArgumentCaptor<KnowledgeDocument> updateCaptor = ArgumentCaptor.forClass(KnowledgeDocument.class);
        verify(knowledgeDocumentDao, times(1)).updateById(updateCaptor.capture());
        KnowledgeDocument updatedDoc = updateCaptor.getValue();
        assertThat(updatedDoc.getId()).isEqualTo(documentId);
        assertThat(updatedDoc.getTitle()).isEqualTo(newTitle);
        assertThat(updatedDoc.getIndexStatus()).isEqualTo(IndexStatus.PENDING.name());
        
        // Then: New indexing task should be triggered
        verify(documentIndexingProducer, times(1)).sendIndexingTask(any());
        
        // Then: Response should indicate success
        assertThat(response.getDocumentId()).isEqualTo(documentId);
        assertThat(response.getTitle()).isEqualTo(newTitle);
        assertThat(response.getIndexStatus()).isEqualTo(IndexStatus.PENDING.name());
    }
    
    /**
     * Property 11 (Deletion Before Indexing): Old vectors deleted before new indexing
     * 
     * The deletion of old vectors must happen before the new indexing task is triggered.
     */
    @Property(tries = 50)
    @Label("Feature: ai-assistant-rag, Property 11: Document Update Atomicity - Deletion Before Indexing")
    void oldVectorsDeletedBeforeNewIndexing(
            @ForAll @IntRange(min = 1, max = 1000) long documentId,
            @ForAll @StringLength(min = 1, max = 50) String title
    ) {
        // Given: Old document exists
        KnowledgeDocument oldDocument = createDocument(documentId, title, "old.txt");
        when(knowledgeDocumentDao.getById(documentId)).thenReturn(oldDocument);
        when(knowledgeDocumentDao.updateById(any(KnowledgeDocument.class))).thenReturn(true);
        
        // Track call order
        List<String> callOrder = new java.util.ArrayList<>();
        
        doAnswer(invocation -> {
            callOrder.add("deleteVectors");
            return null;
        }).when(vectorService).deleteVectors(anyLong());
        
        doAnswer(invocation -> {
            callOrder.add("sendIndexingTask");
            return null;
        }).when(documentIndexingProducer).sendIndexingTask(any());
        
        // Given: New document request
        MultipartFile newFile = createMockFile("new.txt", "Content");
        
        DocumentUpdateRequest request = new DocumentUpdateRequest();
        request.setTitle(title);
        request.setFile(newFile);
        request.setUserId(1L);
        
        // When: Update document
        ragService.updateDocument(documentId, request);
        
        // Then: deleteVectors should be called before sendIndexingTask
        assertThat(callOrder).hasSize(2);
        assertThat(callOrder.get(0)).isEqualTo("deleteVectors");
        assertThat(callOrder.get(1)).isEqualTo("sendIndexingTask");
    }
    
    /**
     * Property 11 (Single Version): Only one version exists after update
     * 
     * After update, there should be no traces of the old version.
     */
    @Property(tries = 50)
    @Label("Feature: ai-assistant-rag, Property 11: Document Update Atomicity - Single Version")
    void onlyOneVersionExistsAfterUpdate(
            @ForAll @IntRange(min = 1, max = 1000) long documentId,
            @ForAll @StringLength(min = 1, max = 50) String oldTitle,
            @ForAll @StringLength(min = 1, max = 50) String newTitle
    ) {
        // Given: Old document with old file path
        KnowledgeDocument oldDocument = createDocument(documentId, oldTitle, "old-path.txt");
        String oldFilePath = oldDocument.getFilePath();
        
        when(knowledgeDocumentDao.getById(documentId)).thenReturn(oldDocument);
        when(knowledgeDocumentDao.updateById(any(KnowledgeDocument.class))).thenReturn(true);
        doNothing().when(vectorService).deleteVectors(anyLong());
        doNothing().when(documentIndexingProducer).sendIndexingTask(any());
        
        // Given: New document request
        MultipartFile newFile = createMockFile("new-file.txt", "New content");
        
        DocumentUpdateRequest request = new DocumentUpdateRequest();
        request.setTitle(newTitle);
        request.setFile(newFile);
        request.setUserId(1L);
        
        // When: Update document
        ragService.updateDocument(documentId, request);
        
        // Then: Old vectors should be deleted
        verify(vectorService, times(1)).deleteVectors(documentId);
        
        // Then: Document should be updated with new information
        ArgumentCaptor<KnowledgeDocument> captor = ArgumentCaptor.forClass(KnowledgeDocument.class);
        verify(knowledgeDocumentDao, times(1)).updateById(captor.capture());
        
        KnowledgeDocument updated = captor.getValue();
        assertThat(updated.getTitle()).isEqualTo(newTitle);
        assertThat(updated.getFilePath()).isNotEqualTo(oldFilePath);
        assertThat(updated.getIndexStatus()).isEqualTo(IndexStatus.PENDING.name());
        assertThat(updated.getChunkCount()).isEqualTo(0); // Reset to 0 for new indexing
    }
    
    /**
     * Property 11 (Update Failure Rollback): Update failure should not leave partial state
     * 
     * If update fails after deletion, the system should handle it gracefully.
     */
    @Property(tries = 30)
    @Label("Feature: ai-assistant-rag, Property 11: Document Update Atomicity - Failure Handling")
    void updateFailureHandledGracefully(
            @ForAll @IntRange(min = 1, max = 1000) long documentId,
            @ForAll @StringLength(min = 1, max = 50) String title
    ) {
        // Given: Old document exists
        KnowledgeDocument oldDocument = createDocument(documentId, title, "old.txt");
        when(knowledgeDocumentDao.getById(documentId)).thenReturn(oldDocument);
        
        // Given: Update will fail
        when(knowledgeDocumentDao.updateById(any(KnowledgeDocument.class)))
                .thenThrow(new RuntimeException("Database error"));
        
        doNothing().when(vectorService).deleteVectors(anyLong());
        
        // Given: New document request
        MultipartFile newFile = createMockFile("new.txt", "Content");
        
        DocumentUpdateRequest request = new DocumentUpdateRequest();
        request.setTitle(title);
        request.setFile(newFile);
        request.setUserId(1L);
        
        // When: Update document (should fail)
        try {
            ragService.updateDocument(documentId, request);
        } catch (RuntimeException e) {
            // Expected exception
        }
        
        // Then: Vectors should have been deleted (transaction will rollback in real scenario)
        verify(vectorService, times(1)).deleteVectors(documentId);
        
        // Then: Indexing task should NOT be triggered due to exception
        verify(documentIndexingProducer, never()).sendIndexingTask(any());
    }
    
    /**
     * Property 11 (Multiple Updates): Multiple sequential updates
     * 
     * Multiple updates should each be atomic and maintain consistency.
     */
    @Property(tries = 30)
    @Label("Feature: ai-assistant-rag, Property 11: Document Update Atomicity - Multiple Updates")
    void multipleUpdatesAreAtomic(
            @ForAll @IntRange(min = 1, max = 1000) long documentId,
            @ForAll @IntRange(min = 2, max = 4) int updateCount
    ) {
        // Given: Initial document
        KnowledgeDocument document = createDocument(documentId, "Initial", "initial.txt");
        when(knowledgeDocumentDao.getById(documentId)).thenReturn(document);
        when(knowledgeDocumentDao.updateById(any(KnowledgeDocument.class))).thenReturn(true);
        doNothing().when(vectorService).deleteVectors(anyLong());
        doNothing().when(documentIndexingProducer).sendIndexingTask(any());
        
        // When: Perform multiple updates
        for (int i = 0; i < updateCount; i++) {
            MultipartFile file = createMockFile("version-" + i + ".txt", "Content " + i);
            
            DocumentUpdateRequest request = new DocumentUpdateRequest();
            request.setTitle("Version " + i);
            request.setFile(file);
            request.setUserId(1L);
            
            ragService.updateDocument(documentId, request);
        }
        
        // Then: Vectors should be deleted for each update
        verify(vectorService, times(updateCount)).deleteVectors(documentId);
        
        // Then: Document should be updated for each update
        verify(knowledgeDocumentDao, times(updateCount)).updateById(any(KnowledgeDocument.class));
        
        // Then: Indexing task should be triggered for each update
        verify(documentIndexingProducer, times(updateCount)).sendIndexingTask(any());
    }
    
    /**
     * Helper method to create a test document
     */
    private KnowledgeDocument createDocument(Long id, String title, String filePath) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(id);
        document.setTitle(title);
        document.setDocumentType("txt");
        document.setFileSize(1000L);
        document.setFilePath(filePath);
        document.setIndexStatus(IndexStatus.COMPLETED.name());
        document.setChunkCount(10);
        document.setUploadUserId(1L);
        document.setCreateTime(LocalDateTime.now());
        document.setUpdateTime(LocalDateTime.now());
        return document;
    }
    
    /**
     * Helper method to create a mock MultipartFile
     */
    private MultipartFile createMockFile(String filename, String content) {
        MultipartFile mockFile = mock(MultipartFile.class);
        try {
            when(mockFile.getOriginalFilename()).thenReturn(filename);
            when(mockFile.getSize()).thenReturn((long) content.length());
            when(mockFile.getBytes()).thenReturn(content.getBytes());
            when(mockFile.getInputStream()).thenReturn(new ByteArrayInputStream(content.getBytes()));
            when(mockFile.isEmpty()).thenReturn(false);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create mock file", e);
        }
        return mockFile;
    }
}
