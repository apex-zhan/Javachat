package com.abin.mallchat.ai.rag.service.impl;

import com.abin.mallchat.ai.common.dao.AIConversationDao;
import com.abin.mallchat.ai.common.dao.KnowledgeDocumentDao;
import com.abin.mallchat.ai.common.domain.entity.KnowledgeDocument;
import com.abin.mallchat.ai.common.domain.enums.IndexStatus;
import com.abin.mallchat.ai.llm.domain.LLMOptions;
import com.abin.mallchat.ai.llm.service.LLMService;
import com.abin.mallchat.ai.rag.domain.dto.RAGQueryRequest;
import com.abin.mallchat.ai.vector.domain.SearchResult;
import com.abin.mallchat.ai.vector.service.EmbeddingService;
import com.abin.mallchat.ai.vector.service.VectorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * RAG查询功能单元测试
 * 
 * @author zxw
 */
@ExtendWith(MockitoExtension.class)
class RAGQueryTest {
    
    @Mock
    private KnowledgeDocumentDao knowledgeDocumentDao;
    
    @Mock
    private AIConversationDao aiConversationDao;
    
    @Mock
    private VectorService vectorService;
    
    @Mock
    private EmbeddingService embeddingService;
    
    @Mock
    private LLMService llmService;
    
    @InjectMocks
    private RAGServiceImpl ragService;
    
    private RAGQueryRequest request;
    private KnowledgeDocument document;
    
    @BeforeEach
    void setUp() {
        request = new RAGQueryRequest();
        request.setQuestion("什么是RAG？");
        request.setDocumentId(1L);
        request.setUserId(100L);
        request.setTopK(5);
        
        document = new KnowledgeDocument();
        document.setId(1L);
        document.setIndexStatus(IndexStatus.COMPLETED.name());
    }
    
    @Test
    void testRAGQuery_WithValidResults_ShouldReturnStreamingResponse() {
        // Given
        when(knowledgeDocumentDao.getById(1L)).thenReturn(document);
        
        float[] mockVector = new float[]{0.1f, 0.2f, 0.3f};
        when(embeddingService.generateEmbedding(anyString())).thenReturn(mockVector);
        
        List<SearchResult> mockResults = Arrays.asList(
                SearchResult.builder()
                        .chunkId(1L)
                        .content("RAG是检索增强生成技术")
                        .score(0.95f)
                        .build(),
                SearchResult.builder()
                        .chunkId(2L)
                        .content("RAG结合了检索和生成")
                        .score(0.85f)
                        .build()
        );
        when(vectorService.search(any(float[].class), anyInt(), anyLong())).thenReturn(mockResults);
        
        Flux<String> mockResponse = Flux.just("RAG", "是", "一种", "技术");
        when(llmService.streamChat(anyString(), any(LLMOptions.class))).thenReturn(mockResponse);
        
        // When
        Flux<String> result = ragService.ragQuery(request);
        
        // Then
        StepVerifier.create(result)
                .expectNext("RAG")
                .expectNext("是")
                .expectNext("一种")
                .expectNext("技术")
                .verifyComplete();
    }
    
    @Test
    void testRAGQuery_WithPendingIndex_ShouldReturnWaitMessage() {
        // Given
        document.setIndexStatus(IndexStatus.PENDING.name());
        when(knowledgeDocumentDao.getById(1L)).thenReturn(document);
        
        // When
        Flux<String> result = ragService.ragQuery(request);
        
        // Then
        StepVerifier.create(result)
                .expectNext("文档正在等待索引处理，请稍后再试。")
                .verifyComplete();
    }
    
    @Test
    void testRAGQuery_WithIndexingStatus_ShouldReturnWaitMessage() {
        // Given
        document.setIndexStatus(IndexStatus.INDEXING.name());
        when(knowledgeDocumentDao.getById(1L)).thenReturn(document);
        
        // When
        Flux<String> result = ragService.ragQuery(request);
        
        // Then
        StepVerifier.create(result)
                .expectNext("文档正在索引中，请稍后再试。")
                .verifyComplete();
    }
    
    @Test
    void testRAGQuery_WithFailedIndex_ShouldReturnErrorMessage() {
        // Given
        document.setIndexStatus(IndexStatus.FAILED.name());
        when(knowledgeDocumentDao.getById(1L)).thenReturn(document);
        
        // When
        Flux<String> result = ragService.ragQuery(request);
        
        // Then
        StepVerifier.create(result)
                .expectNext("文档索引失败，请联系管理员或重新上传文档。")
                .verifyComplete();
    }
    
    @Test
    void testRAGQuery_WithEmptySearchResults_ShouldFallbackToNormalQA() {
        // Given
        when(knowledgeDocumentDao.getById(1L)).thenReturn(document);
        
        float[] mockVector = new float[]{0.1f, 0.2f, 0.3f};
        when(embeddingService.generateEmbedding(anyString())).thenReturn(mockVector);
        
        when(vectorService.search(any(float[].class), anyInt(), anyLong()))
                .thenReturn(Collections.emptyList());
        
        Flux<String> mockResponse = Flux.just("这是", "普通", "回答");
        when(llmService.streamChat(anyString(), any(LLMOptions.class))).thenReturn(mockResponse);
        
        // When
        Flux<String> result = ragService.ragQuery(request);
        
        // Then
        StepVerifier.create(result)
                .expectNext("这是")
                .expectNext("普通")
                .expectNext("回答")
                .verifyComplete();
    }
    
    @Test
    void testRAGQuery_WithNullDocumentId_ShouldPerformGlobalSearch() {
        // Given
        request.setDocumentId(null); // Global search
        
        float[] mockVector = new float[]{0.1f, 0.2f, 0.3f};
        when(embeddingService.generateEmbedding(anyString())).thenReturn(mockVector);
        
        List<SearchResult> mockResults = Arrays.asList(
                SearchResult.builder()
                        .chunkId(1L)
                        .content("全局检索结果")
                        .score(0.90f)
                        .build()
        );
        when(vectorService.search(any(float[].class), anyInt(), isNull())).thenReturn(mockResults);
        
        Flux<String> mockResponse = Flux.just("全局", "回答");
        when(llmService.streamChat(anyString(), any(LLMOptions.class))).thenReturn(mockResponse);
        
        // When
        Flux<String> result = ragService.ragQuery(request);
        
        // Then
        StepVerifier.create(result)
                .expectNext("全局")
                .expectNext("回答")
                .verifyComplete();
    }
}
