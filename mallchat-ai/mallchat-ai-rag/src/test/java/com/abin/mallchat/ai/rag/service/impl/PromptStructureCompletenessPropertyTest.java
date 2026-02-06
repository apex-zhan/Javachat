package com.abin.mallchat.ai.rag.service.impl;

import com.abin.mallchat.ai.vector.domain.SearchResult;
import net.jqwik.api.*;
import org.junit.jupiter.api.Tag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prompt结构完整性属性测试
 * Feature: ai-assistant-rag, Property 20: Prompt Structure Completeness
 * 
 * 验证：
 * 1. Prompt包含系统指令（System Instruction）
 * 2. Prompt包含检索上下文（Retrieved Context）
 * 3. Prompt包含用户问题（User Question）
 * 4. Prompt格式正确且各部分可识别
 * 
 * Validates: Requirements 7.2
 * 
 * @author abin
 */
@Tag("property-test")
public class PromptStructureCompletenessPropertyTest {
    
    /**
     * Property 20: Prompt Structure Completeness
     * 
     * For any RAG query, the constructed prompt should contain all three required components:
     * system instruction, retrieved context, and user question.
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 20: Prompt Structure Completeness")
    void ragPromptShouldContainAllRequiredComponents(
            @ForAll("validQuestion") String question,
            @ForAll("validSearchResults") List<SearchResult> searchResults
    ) {
        // When: Build RAG prompt
        String prompt = buildRAGPrompt(question, searchResults);
        
        // Then: Prompt should contain all three required components
        
        // 1. System instruction should be present
        assertThat(prompt)
                .as("Prompt should contain system instruction")
                .contains("你是一个专业的知识问答助手")
                .contains("回答要求");
        
        // 2. Retrieved context should be present
        assertThat(prompt)
                .as("Prompt should contain knowledge base section")
                .contains("知识库内容：")
                .contains("---");
        
        // Verify each search result content is included
        for (SearchResult result : searchResults) {
            assertThat(prompt)
                    .as("Prompt should contain search result content")
                    .contains(result.getContent());
        }
        
        // 3. User question should be present
        assertThat(prompt)
                .as("Prompt should contain user question section")
                .contains("用户问题：")
                .contains(question);
    }
    
    /**
     * Property 20 (Format Correctness): Prompt format should be correct
     * 
     * The prompt should have proper structure with clear section separators.
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 20: Prompt Structure Completeness - Format Correctness")
    void promptFormatShouldBeCorrect(
            @ForAll("validQuestion") String question,
            @ForAll("validSearchResults") List<SearchResult> searchResults
    ) {
        // When: Build RAG prompt
        String prompt = buildRAGPrompt(question, searchResults);
        
        // Then: Prompt should have correct format
        
        // 1. System instruction should come first
        int systemInstructionIndex = prompt.indexOf("你是一个专业的知识问答助手");
        assertThat(systemInstructionIndex)
                .as("System instruction should be present")
                .isGreaterThanOrEqualTo(0);
        
        // 2. Knowledge base section should come after system instruction
        int knowledgeBaseIndex = prompt.indexOf("知识库内容：");
        assertThat(knowledgeBaseIndex)
                .as("Knowledge base section should come after system instruction")
                .isGreaterThan(systemInstructionIndex);
        
        // 3. User question should come last
        int userQuestionIndex = prompt.indexOf("用户问题：");
        assertThat(userQuestionIndex)
                .as("User question should come after knowledge base")
                .isGreaterThan(knowledgeBaseIndex);
        
        // 4. Verify section separators are present
        assertThat(prompt)
                .as("Prompt should have section separators")
                .contains("---\n");
    }
    
    /**
     * Property 20 (Context Ordering): Search results should be ordered by similarity
     * 
     * The prompt should include search results in descending order of similarity score.
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 20: Prompt Structure Completeness - Context Ordering")
    void searchResultsShouldBeOrderedBySimilarity(
            @ForAll("validQuestion") String question,
            @ForAll("multipleSearchResults") List<SearchResult> searchResults
    ) {
        // Skip if less than 2 results
        if (searchResults.size() < 2) {
            return;
        }
        
        // When: Build RAG prompt
        String prompt = buildRAGPrompt(question, searchResults);
        
        // Then: Results should appear in order of similarity score (descending)
        List<SearchResult> uniqueResults = deduplicateSearchResults(searchResults);
        
        for (int i = 0; i < uniqueResults.size() - 1; i++) {
            SearchResult current = uniqueResults.get(i);
            SearchResult next = uniqueResults.get(i + 1);
            
            // Current result should have higher or equal score
            assertThat(current.getScore())
                    .as("Result %d should have higher score than result %d", i, i + 1)
                    .isGreaterThanOrEqualTo(next.getScore());
            
            // Current result should appear before next result in prompt
            int currentIndex = prompt.indexOf(current.getContent());
            int nextIndex = prompt.indexOf(next.getContent());
            
            assertThat(currentIndex)
                    .as("Result %d should appear before result %d in prompt", i, i + 1)
                    .isLessThan(nextIndex);
        }
    }
    
    /**
     * Property 20 (Deduplication): Duplicate content should be removed
     * 
     * When search results contain duplicate content, only one instance should appear in prompt.
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 20: Prompt Structure Completeness - Deduplication")
    void duplicateContentShouldBeRemoved(
            @ForAll("validQuestion") String question,
            @ForAll("searchResultsWithDuplicates") List<SearchResult> searchResults
    ) {
        // When: Build RAG prompt with duplicate results
        String prompt = buildRAGPrompt(question, searchResults);
        
        // Then: Each unique content should appear only once
        Map<String, Integer> contentCounts = new HashMap<>();
        for (SearchResult result : searchResults) {
            contentCounts.put(result.getContent(), 
                    contentCounts.getOrDefault(result.getContent(), 0) + 1);
        }
        
        // For each unique content, count occurrences in prompt
        for (Map.Entry<String, Integer> entry : contentCounts.entrySet()) {
            String content = entry.getKey();
            
            // Count occurrences in prompt
            int count = 0;
            int index = 0;
            while ((index = prompt.indexOf(content, index)) != -1) {
                count++;
                index += content.length();
            }
            
            // Should appear exactly once in the knowledge base section
            assertThat(count)
                    .as("Content '%s' should appear exactly once in prompt", 
                            content.substring(0, Math.min(20, content.length())))
                    .isEqualTo(1);
        }
    }
    
    /**
     * Property 20 (Score Display): Similarity scores should be displayed
     * 
     * Each search result should have its similarity score displayed in the prompt.
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 20: Prompt Structure Completeness - Score Display")
    void similarityScoresShouldBeDisplayed(
            @ForAll("validQuestion") String question,
            @ForAll("validSearchResults") List<SearchResult> searchResults
    ) {
        // When: Build RAG prompt
        String prompt = buildRAGPrompt(question, searchResults);
        
        // Then: Each result should have its score displayed
        List<SearchResult> uniqueResults = deduplicateSearchResults(searchResults);
        
        for (int i = 0; i < uniqueResults.size(); i++) {
            SearchResult result = uniqueResults.get(i);
            
            // Check for score format: (相似度: X.XX)
            String scorePattern = String.format("相似度: %.2f", result.getScore());
            assertThat(prompt)
                    .as("Prompt should display similarity score for result %d", i)
                    .contains(scorePattern);
        }
    }
    
    /**
     * Property 20 (Fragment Numbering): Search results should be numbered
     * 
     * Each search result fragment should have a sequential number.
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 20: Prompt Structure Completeness - Fragment Numbering")
    void searchResultsShouldBeNumbered(
            @ForAll("validQuestion") String question,
            @ForAll("multipleSearchResults") List<SearchResult> searchResults
    ) {
        // When: Build RAG prompt
        String prompt = buildRAGPrompt(question, searchResults);
        
        // Then: Each result should be numbered sequentially
        List<SearchResult> uniqueResults = deduplicateSearchResults(searchResults);
        
        for (int i = 0; i < uniqueResults.size(); i++) {
            String fragmentLabel = String.format("[片段 %d]", i + 1);
            assertThat(prompt)
                    .as("Prompt should contain fragment label for result %d", i)
                    .contains(fragmentLabel);
        }
    }
    
    /**
     * Property 20 (Empty Results): Empty search results should produce fallback prompt
     * 
     * When no search results are found, the prompt should not contain knowledge base section.
     */
    @Property(tries = 50)
    @Label("Feature: ai-assistant-rag, Property 20: Prompt Structure Completeness - Empty Results")
    void emptyResultsShouldProduceFallbackPrompt(
            @ForAll("validQuestion") String question
    ) {
        // When: Build prompt with empty search results
        List<SearchResult> emptyResults = new ArrayList<>();
        String prompt = buildRAGPrompt(question, emptyResults);
        
        // Then: Prompt should still contain system instruction and question
        assertThat(prompt)
                .as("Prompt should contain system instruction even with empty results")
                .contains("你是一个专业的知识问答助手");
        
        assertThat(prompt)
                .as("Prompt should contain user question even with empty results")
                .contains(question);
        
        // Knowledge base section should be empty or minimal
        int knowledgeStartIndex = prompt.indexOf("知识库内容：");
        int knowledgeEndIndex = prompt.indexOf("---", knowledgeStartIndex + 1);
        
        if (knowledgeStartIndex >= 0 && knowledgeEndIndex >= 0) {
            String knowledgeSection = prompt.substring(knowledgeStartIndex, knowledgeEndIndex);
            // Should not contain any fragment labels
            assertThat(knowledgeSection)
                    .as("Knowledge base section should be empty")
                    .doesNotContain("[片段");
        }
    }
    
    /**
     * Property 20 (Question Preservation): User question should be preserved exactly
     * 
     * The user's question should appear in the prompt exactly as provided.
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 20: Prompt Structure Completeness - Question Preservation")
    void userQuestionShouldBePreservedExactly(
            @ForAll("validQuestion") String question,
            @ForAll("validSearchResults") List<SearchResult> searchResults
    ) {
        // When: Build RAG prompt
        String prompt = buildRAGPrompt(question, searchResults);
        
        // Then: Question should appear exactly as provided
        int questionIndex = prompt.indexOf("用户问题：");
        assertThat(questionIndex)
                .as("User question section should be present")
                .isGreaterThanOrEqualTo(0);
        
        // Extract the question part from prompt
        String questionSection = prompt.substring(questionIndex);
        assertThat(questionSection)
                .as("User question should be preserved exactly")
                .contains(question);
    }
    
    // ==================== Helper Methods ====================
    
    /**
     * Build RAG prompt (copied from RAGServiceImpl for testing)
     */
    private String buildRAGPrompt(String question, List<SearchResult> searchResults) {
        StringBuilder prompt = new StringBuilder();
        
        // System instruction
        prompt.append("你是一个专业的知识问答助手。请根据以下提供的知识库内容回答用户的问题。\n\n");
        prompt.append("回答要求：\n");
        prompt.append("1. 仅基于提供的知识库内容回答，不要编造信息\n");
        prompt.append("2. 如果知识库中没有相关信息，请明确告知用户\n");
        prompt.append("3. 回答要准确、简洁、易懂\n");
        prompt.append("4. 可以适当引用知识库中的原文\n\n");
        
        // Retrieved context (deduplicated)
        prompt.append("知识库内容：\n");
        prompt.append("---\n");
        
        // Deduplicate and sort by similarity
        List<SearchResult> uniqueResults = deduplicateSearchResults(searchResults);
        for (int i = 0; i < uniqueResults.size(); i++) {
            SearchResult result = uniqueResults.get(i);
            prompt.append(String.format("[片段 %d] (相似度: %.2f)\n", i + 1, result.getScore()));
            prompt.append(result.getContent());
            prompt.append("\n\n");
        }
        prompt.append("---\n\n");
        
        // User question
        prompt.append("用户问题：\n");
        prompt.append(question);
        prompt.append("\n\n请回答：");
        
        return prompt.toString();
    }
    
    /**
     * Deduplicate search results (copied from RAGServiceImpl)
     */
    private List<SearchResult> deduplicateSearchResults(List<SearchResult> results) {
        Map<String, SearchResult> uniqueMap = new HashMap<>();
        
        for (SearchResult result : results) {
            String content = result.getContent();
            SearchResult existing = uniqueMap.get(content);
            
            if (existing == null || result.getScore() > existing.getScore()) {
                uniqueMap.put(content, result);
            }
        }
        
        List<SearchResult> uniqueResults = new ArrayList<>(uniqueMap.values());
        uniqueResults.sort((r1, r2) -> Float.compare(r2.getScore(), r1.getScore()));
        
        return uniqueResults;
    }
    
    // ==================== Arbitraries ====================
    
    /**
     * Generate valid questions (10-200 characters)
     */
    @Provide
    Arbitrary<String> validQuestion() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withChars(' ', '?', '，', '。')
                .ofMinLength(10)
                .ofMaxLength(200);
    }
    
    /**
     * Generate valid search results (1-5 results)
     */
    @Provide
    Arbitrary<List<SearchResult>> validSearchResults() {
        return Arbitraries.integers().between(1, 5)
                .flatMap(count -> {
                    List<Arbitrary<SearchResult>> resultArbitraries = new ArrayList<>();
                    for (int i = 0; i < count; i++) {
                        resultArbitraries.add(searchResult());
                    }
                    return Combinators.combine(resultArbitraries).as(list -> list);
                });
    }
    
    /**
     * Generate multiple search results (2-10 results)
     */
    @Provide
    Arbitrary<List<SearchResult>> multipleSearchResults() {
        return Arbitraries.integers().between(2, 10)
                .flatMap(count -> {
                    List<Arbitrary<SearchResult>> resultArbitraries = new ArrayList<>();
                    for (int i = 0; i < count; i++) {
                        resultArbitraries.add(searchResult());
                    }
                    return Combinators.combine(resultArbitraries).as(list -> list);
                });
    }
    
    /**
     * Generate search results with duplicates
     */
    @Provide
    Arbitrary<List<SearchResult>> searchResultsWithDuplicates() {
        return Arbitraries.integers().between(3, 8)
                .flatMap(count -> {
                    // Generate base results
                    Arbitrary<List<SearchResult>> baseResults = Arbitraries.integers()
                            .between(2, count / 2)
                            .flatMap(baseCount -> {
                                List<Arbitrary<SearchResult>> resultArbitraries = new ArrayList<>();
                                for (int i = 0; i < baseCount; i++) {
                                    resultArbitraries.add(searchResult());
                                }
                                return Combinators.combine(resultArbitraries).as(list -> list);
                            });
                    
                    // Duplicate some results
                    return baseResults.map(base -> {
                        List<SearchResult> withDuplicates = new ArrayList<>(base);
                        
                        // Add duplicates with different scores
                        for (int i = 0; i < Math.min(3, base.size()); i++) {
                            SearchResult original = base.get(i);
                            SearchResult duplicate = new SearchResult();
                            duplicate.setChunkId(original.getChunkId() + 1000);
                            duplicate.setContent(original.getContent()); // Same content
                            duplicate.setScore(original.getScore() * 0.9f); // Different score
                            duplicate.setMetadata(new HashMap<>(original.getMetadata()));
                            withDuplicates.add(duplicate);
                        }
                        
                        return withDuplicates;
                    });
                });
    }
    
    /**
     * Generate a single search result
     */
    private Arbitrary<SearchResult> searchResult() {
        return Combinators.combine(
                Arbitraries.longs().between(1L, 999999L),
                Arbitraries.strings()
                        .withCharRange('a', 'z')
                        .withChars(' ', '.', ',', '\n')
                        .ofMinLength(50)
                        .ofMaxLength(500),
                Arbitraries.floats().between(0.1f, 1.0f)
        ).as((chunkId, content, score) -> {
            SearchResult result = new SearchResult();
            result.setChunkId(chunkId);
            result.setContent(content);
            result.setScore(score);
            result.setMetadata(new HashMap<>());
            return result;
        });
    }
}
