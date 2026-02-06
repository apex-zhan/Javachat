package com.abin.mallchat.ai.rag.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 属性测试：向量检索指标
 * Feature: ai-assistant-rag, Property 31: Vector Search Metrics
 * Validates: Requirements 12.3
 * 
 * 验证每次向量检索都记录指标，且指标数据准确
 * 
 * @author AI Assistant
 * @date 2025-01-07
 */
class VectorSearchMetricsPropertyTest {

    private VectorSearchMetrics vectorSearchMetrics;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        vectorSearchMetrics = new VectorSearchMetrics(meterRegistry);
    }

    /**
     * Property 31: Vector Search Metrics Recording
     * For any vector search operation, metrics should be recorded including:
     * - query time
     * - number of results
     * - top score
     * - hit/miss status
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 31: Vector Search Metrics Recording")
    void everySearchShouldRecordMetrics(
            @ForAll @LongRange(min = 1, max = 5000) long queryTime,
            @ForAll @IntRange(min = 0, max = 100) int numResults,
            @ForAll("validScores") Float topScore) {
        
        // Given: Initial metrics state
        double initialTotalCount = vectorSearchMetrics.getTotalSearchCount();
        double initialHitCount = vectorSearchMetrics.getHitCount();
        double initialMissCount = vectorSearchMetrics.getMissCount();
        
        // When: Record a search
        vectorSearchMetrics.recordSearch(queryTime, numResults, topScore);
        
        // Then: Verify metrics are updated
        double newTotalCount = vectorSearchMetrics.getTotalSearchCount();
        assertThat(newTotalCount).isEqualTo(initialTotalCount + 1);
        
        // Verify hit/miss is recorded correctly
        if (numResults > 0) {
            double newHitCount = vectorSearchMetrics.getHitCount();
            assertThat(newHitCount).isEqualTo(initialHitCount + 1);
        } else {
            double newMissCount = vectorSearchMetrics.getMissCount();
            assertThat(newMissCount).isEqualTo(initialMissCount + 1);
        }
    }

    /**
     * Property: Hit rate calculation accuracy
     * For any sequence of searches, the hit rate should be calculated correctly
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 31: Hit Rate Accuracy")
    void hitRateShouldBeCalculatedCorrectly(
            @ForAll @IntRange(min = 1, max = 20) int numSearches,
            @ForAll @IntRange(min = 0, max = 20) int numHits) {
        
        // Given: Fresh metrics
        meterRegistry = new SimpleMeterRegistry();
        vectorSearchMetrics = new VectorSearchMetrics(meterRegistry);
        
        // Ensure numHits <= numSearches
        int actualHits = Math.min(numHits, numSearches);
        int actualMisses = numSearches - actualHits;
        
        // When: Record searches with hits
        for (int i = 0; i < actualHits; i++) {
            vectorSearchMetrics.recordSearch(100, 5, 0.9f);
        }
        
        // Record searches with misses
        for (int i = 0; i < actualMisses; i++) {
            vectorSearchMetrics.recordSearch(100, 0, null);
        }
        
        // Then: Verify hit rate
        double expectedHitRate = (double) actualHits / numSearches;
        double actualHitRate = vectorSearchMetrics.getHitRate();
        
        assertThat(actualHitRate).isCloseTo(expectedHitRate, Offset.offset(0.01));
    }

    /**
     * Property: Query time statistics accuracy
     * For any sequence of searches, average and max query times should be accurate
     */
    @Property(tries = 50)
    @Label("Feature: ai-assistant-rag, Property 31: Query Time Statistics")
    void queryTimeStatisticsShouldBeAccurate(
            @ForAll("queryTimes") java.util.List<Long> queryTimes) {
        
        // Given: Fresh metrics
        meterRegistry = new SimpleMeterRegistry();
        vectorSearchMetrics = new VectorSearchMetrics(meterRegistry);
        
        // When: Record searches with different query times
        for (Long queryTime : queryTimes) {
            vectorSearchMetrics.recordSearch(queryTime, 1, 0.8f);
        }
        
        // Then: Verify max query time
        long expectedMax = queryTimes.stream().max(Long::compareTo).orElse(0L);
        double actualMax = vectorSearchMetrics.getMaxQueryTime();
        
        assertThat(actualMax).isGreaterThanOrEqualTo(expectedMax);
        
        // Verify average query time is within reasonable range
        double expectedAvg = queryTimes.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);
        double actualAvg = vectorSearchMetrics.getAverageQueryTime();
        
        // Allow some tolerance for floating point calculations
        assertThat(actualAvg).isCloseTo(expectedAvg, Offset.offset(expectedAvg * 0.1 + 1));
    }

    /**
     * Property: Metrics should accumulate correctly
     * For any sequence of searches, total count should equal hits + misses
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 31: Metrics Accumulation")
    void totalCountShouldEqualHitsPlusMisses(
            @ForAll @IntRange(min = 1, max = 50) int numSearches) {
        
        // Given: Fresh metrics
        meterRegistry = new SimpleMeterRegistry();
        vectorSearchMetrics = new VectorSearchMetrics(meterRegistry);
        
        // When: Record random searches
        for (int i = 0; i < numSearches; i++) {
            int numResults = i % 3 == 0 ? 0 : (i % 5 + 1);
            vectorSearchMetrics.recordSearch(100, numResults, 0.8f);
        }
        
        // Then: Verify total = hits + misses
        double totalCount = vectorSearchMetrics.getTotalSearchCount();
        double hitCount = vectorSearchMetrics.getHitCount();
        double missCount = vectorSearchMetrics.getMissCount();
        
        assertThat(totalCount).isEqualTo(hitCount + missCount);
        assertThat(totalCount).isEqualTo(numSearches);
    }

    /**
     * Property: Zero results should be recorded as miss
     * For any search with zero results, it should be counted as a miss
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 31: Zero Results Miss")
    void zeroResultsShouldBeRecordedAsMiss(
            @ForAll @LongRange(min = 1, max = 1000) long queryTime) {
        
        // Given: Fresh metrics
        meterRegistry = new SimpleMeterRegistry();
        vectorSearchMetrics = new VectorSearchMetrics(meterRegistry);
        
        double initialMissCount = vectorSearchMetrics.getMissCount();
        
        // When: Record search with zero results
        vectorSearchMetrics.recordSearch(queryTime, 0, null);
        
        // Then: Miss count should increase
        double newMissCount = vectorSearchMetrics.getMissCount();
        assertThat(newMissCount).isEqualTo(initialMissCount + 1);
        
        // Hit count should not change
        assertThat(vectorSearchMetrics.getHitCount()).isEqualTo(0);
    }

    /**
     * Property: Non-zero results should be recorded as hit
     * For any search with results, it should be counted as a hit
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 31: Non-Zero Results Hit")
    void nonZeroResultsShouldBeRecordedAsHit(
            @ForAll @LongRange(min = 1, max = 1000) long queryTime,
            @ForAll @IntRange(min = 1, max = 100) int numResults) {
        
        // Given: Fresh metrics
        meterRegistry = new SimpleMeterRegistry();
        vectorSearchMetrics = new VectorSearchMetrics(meterRegistry);
        
        double initialHitCount = vectorSearchMetrics.getHitCount();
        
        // When: Record search with results
        vectorSearchMetrics.recordSearch(queryTime, numResults, 0.9f);
        
        // Then: Hit count should increase
        double newHitCount = vectorSearchMetrics.getHitCount();
        assertThat(newHitCount).isEqualTo(initialHitCount + 1);
        
        // Miss count should not change
        assertThat(vectorSearchMetrics.getMissCount()).isEqualTo(0);
    }

    // ==================== Generators ====================

    @Provide
    Arbitrary<Float> validScores() {
        return Arbitraries.floats()
                .between(0.0f, 1.0f)
                .injectNull(0.1); // 10% chance of null
    }

    @Provide
    Arbitrary<java.util.List<Long>> queryTimes() {
        return Arbitraries.longs()
                .between(1L, 5000L)
                .list()
                .ofMinSize(1)
                .ofMaxSize(20);
    }
}
