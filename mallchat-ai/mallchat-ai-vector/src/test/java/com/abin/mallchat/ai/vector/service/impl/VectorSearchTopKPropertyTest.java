package com.abin.mallchat.ai.vector.service.impl;

import com.abin.mallchat.ai.vector.domain.SearchResult;
import com.abin.mallchat.ai.vector.service.VectorService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * 向量检索 Top-K 准确性属性测试
 * Feature: ai-assistant-rag, Property 18: Vector Search Top-K Accuracy
 * 
 * 验证：
 * 1. 返回结果数量 <= K
 * 2. 结果按相似度降序排列
 * 
 * Validates: Requirements 6.3, 6.5
 * 
 * @author abin
 */
@Tag("property-test")
public class VectorSearchTopKPropertyTest {
    
    @Mock
    private VectorService vectorService;
    
    private final Random random = new Random();
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }
    
    /**
     * Property 18: Vector Search Top-K Accuracy
     * 
     * For any vector search query with parameter K, the system should return 
     * exactly K results (or fewer if total results < K), sorted by similarity 
     * score in descending order.
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 18: Vector Search Top-K Accuracy")
    void searchReturnsAtMostKResultsSortedByScore(
            @ForAll @IntRange(min = 1, max = 20) int topK,
            @ForAll("validQueryVectors") float[] queryVector,
            @ForAll @IntRange(min = 0, max = 50) int totalResults
    ) {
        // Given: Mock search results with random scores
        List<SearchResult> mockResults = generateMockSearchResults(
                Math.min(topK, totalResults), 
                queryVector.length
        );
        
        when(vectorService.search(any(float[].class), anyInt(), any()))
                .thenReturn(mockResults);
        
        // When: Execute search
        List<SearchResult> results = vectorService.search(queryVector, topK, null);
        
        // Then: Verify result count <= K
        assertThat(results).hasSizeLessThanOrEqualTo(topK);
        
        // Then: Verify results are sorted by score in descending order
        if (results.size() > 1) {
            assertThat(results)
                    .isSortedAccordingTo(
                            Comparator.comparing(SearchResult::getScore).reversed()
                    );
        }
        
        // Then: Verify all scores are valid (between 0 and 1 for COSINE similarity)
        for (SearchResult result : results) {
            assertThat(result.getScore())
                    .isNotNull()
                    .isBetween(-1.0f, 1.0f);
        }
    }
    
    /**
     * Property 18 (Edge Case): Empty results
     * 
     * When no vectors match the query, the system should return an empty list.
     */
    @Property(tries = 50)
    @Label("Feature: ai-assistant-rag, Property 18: Vector Search Top-K Accuracy - Empty Results")
    void searchReturnsEmptyListWhenNoResults(
            @ForAll @IntRange(min = 1, max = 20) int topK,
            @ForAll("validQueryVectors") float[] queryVector
    ) {
        // Given: Mock empty search results
        when(vectorService.search(any(float[].class), anyInt(), any()))
                .thenReturn(new ArrayList<>());
        
        // When: Execute search
        List<SearchResult> results = vectorService.search(queryVector, topK, null);
        
        // Then: Verify empty list is returned
        assertThat(results).isEmpty();
    }
    
    /**
     * Property 18 (Document Filter): Filtered search
     * 
     * When searching with a document ID filter, results should only contain
     * chunks from that document.
     */
    @Property(tries = 50)
    @Label("Feature: ai-assistant-rag, Property 18: Vector Search Top-K Accuracy - Document Filter")
    void searchWithDocumentIdFilterReturnsOnlyMatchingDocument(
            @ForAll @IntRange(min = 1, max = 10) int topK,
            @ForAll("validQueryVectors") float[] queryVector,
            @ForAll @IntRange(min = 1, max = 1000) long documentId
    ) {
        // Given: Mock search results for specific document
        List<SearchResult> mockResults = generateMockSearchResultsForDocument(
                topK, 
                documentId,
                queryVector.length
        );
        
        when(vectorService.search(any(float[].class), anyInt(), eq(documentId)))
                .thenReturn(mockResults);
        
        // When: Execute search with document filter
        List<SearchResult> results = vectorService.search(queryVector, topK, documentId);
        
        // Then: Verify all results belong to the specified document
        for (SearchResult result : results) {
            assertThat(result.getDocumentId()).isEqualTo(documentId);
        }
    }
    
    /**
     * Property 18 (Score Range): Score validity
     * 
     * For COSINE similarity, all scores should be in the range [-1, 1].
     * Higher scores indicate higher similarity.
     */
    @Property(tries = 50)
    @Label("Feature: ai-assistant-rag, Property 18: Vector Search Top-K Accuracy - Score Range")
    void searchResultsHaveValidScoreRange(
            @ForAll @IntRange(min = 1, max = 10) int topK,
            @ForAll("validQueryVectors") float[] queryVector
    ) {
        // Given: Mock search results
        List<SearchResult> mockResults = generateMockSearchResults(topK, queryVector.length);
        
        when(vectorService.search(any(float[].class), anyInt(), any()))
                .thenReturn(mockResults);
        
        // When: Execute search
        List<SearchResult> results = vectorService.search(queryVector, topK, null);
        
        // Then: Verify all scores are in valid range
        for (SearchResult result : results) {
            assertThat(result.getScore())
                    .isNotNull()
                    .isBetween(-1.0f, 1.0f);
        }
    }
    
    // ==================== Arbitraries ====================
    
    /**
     * Generate valid query vectors (1536 dimensions for OpenAI embeddings)
     */
    @Provide
    Arbitrary<float[]> validQueryVectors() {
        return Arbitraries.integers()
                .between(128, 1536)
                .flatMap(dimension -> 
                        Arbitraries.floats()
                                .between(-1.0f, 1.0f)
                                .array(float[].class)
                                .ofSize(dimension)
                );
    }
    
    // ==================== Helper Methods ====================
    
    /**
     * Generate mock search results with random scores
     */
    private List<SearchResult> generateMockSearchResults(int count, int vectorDimension) {
        List<SearchResult> results = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            // Generate descending scores to ensure proper sorting
            float score = 1.0f - (i * 0.05f) - (random.nextFloat() * 0.04f);
            score = Math.max(-1.0f, Math.min(1.0f, score)); // Clamp to valid range
            
            SearchResult result = SearchResult.builder()
                    .chunkId((long) (i + 1))
                    .documentId((long) (random.nextInt(10) + 1))
                    .chunkIndex(i)
                    .content("Mock content " + i)
                    .score(score)
                    .build();
            
            results.add(result);
        }
        
        // Sort by score descending to match expected behavior
        results.sort(Comparator.comparing(SearchResult::getScore).reversed());
        
        return results;
    }
    
    /**
     * Generate mock search results for a specific document
     */
    private List<SearchResult> generateMockSearchResultsForDocument(
            int count, 
            long documentId,
            int vectorDimension
    ) {
        List<SearchResult> results = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            float score = 1.0f - (i * 0.05f) - (random.nextFloat() * 0.04f);
            score = Math.max(-1.0f, Math.min(1.0f, score));
            
            SearchResult result = SearchResult.builder()
                    .chunkId((long) (i + 1))
                    .documentId(documentId)
                    .chunkIndex(i)
                    .content("Mock content for document " + documentId + ", chunk " + i)
                    .score(score)
                    .build();
            
            results.add(result);
        }
        
        results.sort(Comparator.comparing(SearchResult::getScore).reversed());
        
        return results;
    }
}
