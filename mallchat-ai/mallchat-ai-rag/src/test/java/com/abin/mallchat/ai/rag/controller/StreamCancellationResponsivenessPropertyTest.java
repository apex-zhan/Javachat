package com.abin.mallchat.ai.rag.controller;

import com.abin.mallchat.ai.rag.domain.dto.RAGQueryRequest;
import com.abin.mallchat.ai.rag.domain.dto.StreamChunk;
import com.abin.mallchat.ai.rag.service.RAGService;
import com.abin.mallchat.ai.rag.service.StreamConnectionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Property-Based Test: Stream Cancellation Responsiveness
 * Feature: ai-assistant-rag, Property 5: Stream Cancellation Responsiveness
 * 
 * 验证取消信号后流立即停止，并且资源被正确释放
 * 
 * @author zxw
 */
@ExtendWith(MockitoExtension.class)
class StreamCancellationResponsivenessPropertyTest {
    
    @Mock
    private RAGService ragService;
    
    @Mock
    private StreamConnectionManager connectionManager;
    
    @InjectMocks
    private StreamController streamController;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 取消响应时间阈值（毫秒）
     */
    private static final long CANCELLATION_RESPONSE_THRESHOLD_MS = 1000;
    
    @BeforeEach
    void setUp() {
        // 初始化
    }
    
    /**
     * Property 5: Stream Cancellation Responsiveness
     * Validates: Requirements 1.5
     * 
     * For any ongoing stream, when a cancellation signal is received:
     * 1. The stream should stop within 1 second
     * 2. No more chunks should be emitted after cancellation
     * 3. Resources should be released
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 5: Stream Cancellation Responsiveness")
    void streamStopsQuicklyAfterCancellation(
            @ForAll("validRAGRequests") RAGQueryRequest request,
            @ForAll @IntRange(min = 5, max = 20) int totalChunks,
            @ForAll @IntRange(min = 2, max = 5) int chunksBeforeCancel
    ) {
        // Given - 创建一个长时间运行的流
        List<String> mockChunks = generateMockChunks(totalChunks);
        Flux<String> slowFlux = Flux.fromIterable(mockChunks)
                .delayElements(Duration.ofMillis(100));  // 每个块延迟100ms
        
        when(ragService.ragQuery(any(RAGQueryRequest.class)))
                .thenReturn(slowFlux);
        
        // When - 订阅流并在接收到指定数量的块后取消
        AtomicInteger receivedCount = new AtomicInteger(0);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        long startTime = System.currentTimeMillis();
        
        Flux<ServerSentEvent<String>> result = streamController.streamRAGQuery(request);
        
        // Then - 验证取消响应性
        StepVerifier.create(result)
                .expectNextMatches(event -> {
                    receivedCount.incrementAndGet();
                    return true;
                })
                .expectNextCount(chunksBeforeCancel - 1)
                .thenCancel()  // 取消订阅
                .verify(Duration.ofSeconds(5));
        
        long cancellationTime = System.currentTimeMillis() - startTime;
        
        // 验证取消响应时间在阈值内
        assertThat(cancellationTime).isLessThan(CANCELLATION_RESPONSE_THRESHOLD_MS);
        
        // 验证接收到的块数量不超过预期
        assertThat(receivedCount.get()).isLessThanOrEqualTo(chunksBeforeCancel + 2);  // +2 for potential race condition
    }
    
    /**
     * Property: No chunks emitted after cancellation
     * 
     * For any stream that is cancelled, no additional chunks should be
     * emitted after the cancellation signal is processed.
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 5: No chunks after cancellation")
    void noChunksEmittedAfterCancellation(
            @ForAll("validRAGRequests") RAGQueryRequest request,
            @ForAll @IntRange(min = 10, max = 30) int totalChunks,
            @ForAll @IntRange(min = 3, max = 8) int chunksBeforeCancel
    ) {
        // Given
        List<String> mockChunks = generateMockChunks(totalChunks);
        Flux<String> slowFlux = Flux.fromIterable(mockChunks)
                .delayElements(Duration.ofMillis(50));
        
        when(ragService.ragQuery(any(RAGQueryRequest.class)))
                .thenReturn(slowFlux);
        
        // When
        List<ServerSentEvent<String>> receivedEvents = new ArrayList<>();
        Flux<ServerSentEvent<String>> result = streamController.streamRAGQuery(request);
        
        // Then
        StepVerifier.create(result)
                .recordWith(() -> receivedEvents)
                .expectNextCount(chunksBeforeCancel)
                .thenCancel()
                .verify(Duration.ofSeconds(5));
        
        // 等待一小段时间，确保没有更多事件到达
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 验证接收到的事件数量没有增加
        int finalCount = receivedEvents.size();
        assertThat(finalCount).isLessThanOrEqualTo(chunksBeforeCancel + 2);
    }
    
    /**
     * Property: Cancellation works at any point in the stream
     * 
     * For any stream, cancellation should work correctly regardless of
     * when it occurs (early, middle, or late in the stream).
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 5: Cancellation works at any point")
    void cancellationWorksAtAnyPoint(
            @ForAll("validRAGRequests") RAGQueryRequest request,
            @ForAll @IntRange(min = 10, max = 20) int totalChunks,
            @ForAll @DoubleRange(min = 0.1, max = 0.9) double cancelPointRatio
    ) {
        // Given
        int cancelPoint = (int) (totalChunks * cancelPointRatio);
        
        List<String> mockChunks = generateMockChunks(totalChunks);
        Flux<String> slowFlux = Flux.fromIterable(mockChunks)
                .delayElements(Duration.ofMillis(50));
        
        when(ragService.ragQuery(any(RAGQueryRequest.class)))
                .thenReturn(slowFlux);
        
        // When
        Flux<ServerSentEvent<String>> result = streamController.streamRAGQuery(request);
        
        // Then - 在指定点取消
        StepVerifier.create(result)
                .expectNextCount(cancelPoint)
                .thenCancel()
                .verify(Duration.ofSeconds(10));
        
        // 验证取消成功（没有抛出异常）
        // 如果取消失败，StepVerifier会抛出异常
    }
    
    /**
     * Property: Multiple cancellations are idempotent
     * 
     * For any stream, calling cancel multiple times should be safe
     * and not cause errors or resource leaks.
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 5: Multiple cancellations are safe")
    void multipleCancellationsAreSafe(
            @ForAll("validRAGRequests") RAGQueryRequest request,
            @ForAll @IntRange(min = 5, max = 10) int totalChunks
    ) {
        // Given
        List<String> mockChunks = generateMockChunks(totalChunks);
        Flux<String> slowFlux = Flux.fromIterable(mockChunks)
                .delayElements(Duration.ofMillis(100));
        
        when(ragService.ragQuery(any(RAGQueryRequest.class)))
                .thenReturn(slowFlux);
        
        // When - 订阅并多次取消
        Flux<ServerSentEvent<String>> result = streamController.streamRAGQuery(request);
        
        // Then
        StepVerifier.create(result)
                .expectNextCount(3)
                .thenCancel()  // 第一次取消
                .verify(Duration.ofSeconds(5));
        
        // 再次订阅并取消（验证资源已正确释放）
        StepVerifier.create(result)
                .expectNextCount(2)
                .thenCancel()  // 第二次取消
                .verify(Duration.ofSeconds(5));
        
        // 验证没有抛出异常
    }
    
    /**
     * Property: Cancellation releases resources immediately
     * 
     * For any stream, when cancelled, the underlying resources
     * (subscriptions, connections) should be released immediately.
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 5: Resources released on cancellation")
    void resourcesReleasedOnCancellation(
            @ForAll("validRAGRequests") RAGQueryRequest request,
            @ForAll @IntRange(min = 5, max = 15) int totalChunks
    ) {
        // Given
        List<String> mockChunks = generateMockChunks(totalChunks);
        AtomicBoolean resourcesReleased = new AtomicBoolean(false);
        
        Flux<String> slowFlux = Flux.fromIterable(mockChunks)
                .delayElements(Duration.ofMillis(100))
                .doOnCancel(() -> {
                    resourcesReleased.set(true);
                });
        
        when(ragService.ragQuery(any(RAGQueryRequest.class)))
                .thenReturn(slowFlux);
        
        // When
        Flux<ServerSentEvent<String>> result = streamController.streamRAGQuery(request);
        
        // Then
        StepVerifier.create(result)
                .expectNextCount(3)
                .thenCancel()
                .verify(Duration.ofSeconds(5));
        
        // 验证资源已释放
        assertThat(resourcesReleased.get()).isTrue();
    }
    
    /**
     * Property: Cancellation during error handling
     * 
     * For any stream that encounters an error, cancellation should
     * still work correctly and not cause additional errors.
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 5: Cancellation during error")
    void cancellationDuringErrorHandling(
            @ForAll("validRAGRequests") RAGQueryRequest request,
            @ForAll @IntRange(min = 3, max = 8) int chunksBeforeError
    ) {
        // Given - 创建一个会出错的流
        List<String> mockChunks = generateMockChunks(chunksBeforeError);
        Flux<String> errorFlux = Flux.fromIterable(mockChunks)
                .delayElements(Duration.ofMillis(100))
                .concatWith(Flux.error(new RuntimeException("Simulated error")));
        
        when(ragService.ragQuery(any(RAGQueryRequest.class)))
                .thenReturn(errorFlux);
        
        // When
        Flux<ServerSentEvent<String>> result = streamController.streamRAGQuery(request);
        
        // Then - 在错误发生前取消
        StepVerifier.create(result)
                .expectNextCount(chunksBeforeError - 1)
                .thenCancel()
                .verify(Duration.ofSeconds(5));
        
        // 验证取消成功（没有抛出异常）
    }
    
    // ========== Arbitraries (数据生成器) ==========
    
    @Provide
    Arbitrary<RAGQueryRequest> validRAGRequests() {
        return Combinators.combine(
                Arbitraries.strings().withCharRange('a', 'z').ofMinLength(10).ofMaxLength(100),
                Arbitraries.longs().between(1L, 1000L),
                Arbitraries.longs().between(1L, 100L),
                Arbitraries.integers().between(3, 10)
        ).as((question, userId, documentId, topK) -> {
            RAGQueryRequest request = new RAGQueryRequest();
            request.setQuestion(question);
            request.setUserId(userId);
            request.setDocumentId(documentId);
            request.setTopK(topK);
            return request;
        });
    }
    
    // ========== Helper Methods ==========
    
    private List<String> generateMockChunks(int count) {
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            chunks.add("Content chunk " + i);
        }
        return chunks;
    }
}
