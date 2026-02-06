package com.abin.mallchat.ai.rag.controller;

import com.abin.mallchat.ai.rag.domain.dto.RAGQueryRequest;
import com.abin.mallchat.ai.rag.domain.dto.StreamChunk;
import com.abin.mallchat.ai.rag.service.RAGService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Property-Based Test: Stream Completion Marker
 * Feature: ai-assistant-rag, Property 8: Stream Completion Marker
 * 
 * 验证每个完成的流有结束标记，并且标记格式正确
 * 
 * @author zxw
 */
@ExtendWith(MockitoExtension.class)
class StreamCompletionMarkerPropertyTest {
    
    @Mock
    private RAGService ragService;
    
    @InjectMocks
    private StreamController streamController;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @BeforeEach
    void setUp() {
        // 初始化
    }
    
    /**
     * Property 8: Stream Completion Marker
     * Validates: Requirements 3.4
     * 
     * For any completed stream response, the final chunk should contain:
     * 1. A completion marker (finished=true)
     * 2. Correct format (valid JSON)
     * 3. Event type "done"
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 8: Stream Completion Marker")
    void completedStreamHasFinishedMarker(
            @ForAll("validRAGRequests") RAGQueryRequest request,
            @ForAll @IntRange(min = 1, max = 10) int chunkCount
    ) {
        // Given - 模拟RAG服务返回流式响应
        List<String> mockChunks = generateMockChunks(chunkCount);
        when(ragService.ragQuery(any(RAGQueryRequest.class)))
                .thenReturn(Flux.fromIterable(mockChunks));
        
        // When - 调用流式查询
        Flux<ServerSentEvent<String>> result = streamController.streamRAGQuery(request);
        
        // Then - 收集所有事件
        List<ServerSentEvent<String>> events = new ArrayList<>();
        StepVerifier.create(result)
                .recordWith(() -> events)
                .expectNextCount(chunkCount + 1)  // 内容块 + 结束标记
                .expectComplete()
                .verify(Duration.ofSeconds(10));
        
        // 验证最后一个事件是结束标记
        assertThat(events).isNotEmpty();
        ServerSentEvent<String> lastEvent = events.get(events.size() - 1);
        
        // 验证事件类型是 "done"
        assertThat(lastEvent.event()).isEqualTo("done");
        
        // 验证数据格式正确
        assertThat(lastEvent.data()).isNotNull();
        
        // 解析StreamChunk
        try {
            StreamChunk chunk = objectMapper.readValue(lastEvent.data(), StreamChunk.class);
            
            // 验证finished标记为true
            assertThat(chunk.getFinished()).isTrue();
            
            // 验证内容为空（结束标记不包含内容）
            assertThat(chunk.getContent()).isEmpty();
            
            // 验证索引正确（应该等于内容块数量）
            assertThat(chunk.getIndex()).isEqualTo(chunkCount);
            
            // 验证时间戳存在
            assertThat(chunk.getTimestamp()).isNotNull();
            assertThat(chunk.getTimestamp()).isGreaterThan(0L);
            
        } catch (Exception e) {
            throw new AssertionError("Failed to parse StreamChunk: " + e.getMessage(), e);
        }
    }
    
    /**
     * Property: All content chunks have finished=false
     * 
     * For any stream response, all content chunks (non-final) should have
     * finished=false to distinguish them from the completion marker.
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 8: Content chunks not finished")
    void contentChunksHaveFinishedFalse(
            @ForAll("validRAGRequests") RAGQueryRequest request,
            @ForAll @IntRange(min = 2, max = 10) int chunkCount
    ) {
        // Given
        List<String> mockChunks = generateMockChunks(chunkCount);
        when(ragService.ragQuery(any(RAGQueryRequest.class)))
                .thenReturn(Flux.fromIterable(mockChunks));
        
        // When
        Flux<ServerSentEvent<String>> result = streamController.streamRAGQuery(request);
        
        // Then - 收集所有事件
        List<ServerSentEvent<String>> events = new ArrayList<>();
        StepVerifier.create(result)
                .recordWith(() -> events)
                .expectNextCount(chunkCount + 1)
                .expectComplete()
                .verify(Duration.ofSeconds(10));
        
        // 验证除最后一个外的所有事件都是内容块
        for (int i = 0; i < events.size() - 1; i++) {
            ServerSentEvent<String> event = events.get(i);
            
            // 跳过心跳消息
            if ("heartbeat".equals(event.event())) {
                continue;
            }
            
            // 验证事件类型是 "message"
            assertThat(event.event()).isEqualTo("message");
            
            try {
                StreamChunk chunk = objectMapper.readValue(event.data(), StreamChunk.class);
                
                // 验证finished为false
                assertThat(chunk.getFinished()).isFalse();
                
                // 验证有内容
                assertThat(chunk.getContent()).isNotEmpty();
                
            } catch (Exception e) {
                throw new AssertionError("Failed to parse content chunk: " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * Property: Stream completion marker format is valid JSON
     * 
     * For any completed stream, the completion marker should be valid JSON
     * that can be parsed into a StreamChunk object.
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 8: Completion marker is valid JSON")
    void completionMarkerIsValidJSON(
            @ForAll("validRAGRequests") RAGQueryRequest request,
            @ForAll @IntRange(min = 1, max = 5) int chunkCount
    ) {
        // Given
        List<String> mockChunks = generateMockChunks(chunkCount);
        when(ragService.ragQuery(any(RAGQueryRequest.class)))
                .thenReturn(Flux.fromIterable(mockChunks));
        
        // When
        Flux<ServerSentEvent<String>> result = streamController.streamRAGQuery(request);
        
        // Then
        List<ServerSentEvent<String>> events = new ArrayList<>();
        StepVerifier.create(result)
                .recordWith(() -> events)
                .expectNextCount(chunkCount + 1)
                .expectComplete()
                .verify(Duration.ofSeconds(10));
        
        // 获取最后一个事件
        ServerSentEvent<String> lastEvent = events.get(events.size() - 1);
        
        // 验证可以解析为StreamChunk
        try {
            StreamChunk chunk = objectMapper.readValue(lastEvent.data(), StreamChunk.class);
            
            // 验证所有必需字段都存在
            assertThat(chunk.getIndex()).isNotNull();
            assertThat(chunk.getContent()).isNotNull();
            assertThat(chunk.getFinished()).isNotNull();
            assertThat(chunk.getTimestamp()).isNotNull();
            
        } catch (Exception e) {
            throw new AssertionError("Completion marker is not valid JSON: " + e.getMessage(), e);
        }
    }
    
    /**
     * Property: Only one completion marker per stream
     * 
     * For any stream response, there should be exactly one event with
     * event type "done" and finished=true.
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 8: Only one completion marker")
    void onlyOneCompletionMarkerPerStream(
            @ForAll("validRAGRequests") RAGQueryRequest request,
            @ForAll @IntRange(min = 1, max = 10) int chunkCount
    ) {
        // Given
        List<String> mockChunks = generateMockChunks(chunkCount);
        when(ragService.ragQuery(any(RAGQueryRequest.class)))
                .thenReturn(Flux.fromIterable(mockChunks));
        
        // When
        Flux<ServerSentEvent<String>> result = streamController.streamRAGQuery(request);
        
        // Then
        List<ServerSentEvent<String>> events = new ArrayList<>();
        StepVerifier.create(result)
                .recordWith(() -> events)
                .expectNextCount(chunkCount + 1)
                .expectComplete()
                .verify(Duration.ofSeconds(10));
        
        // 统计 "done" 事件数量
        long doneEventCount = events.stream()
                .filter(event -> "done".equals(event.event()))
                .count();
        
        // 验证只有一个 "done" 事件
        assertThat(doneEventCount).isEqualTo(1);
        
        // 统计 finished=true 的数量
        long finishedCount = events.stream()
                .filter(event -> {
                    try {
                        StreamChunk chunk = objectMapper.readValue(event.data(), StreamChunk.class);
                        return Boolean.TRUE.equals(chunk.getFinished());
                    } catch (Exception e) {
                        return false;
                    }
                })
                .count();
        
        // 验证只有一个 finished=true
        assertThat(finishedCount).isEqualTo(1);
    }
    
    /**
     * Property: Completion marker is always the last event
     * 
     * For any stream response, the completion marker should always be
     * the last event emitted (excluding heartbeats).
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 8: Completion marker is last")
    void completionMarkerIsLastEvent(
            @ForAll("validRAGRequests") RAGQueryRequest request,
            @ForAll @IntRange(min = 1, max = 10) int chunkCount
    ) {
        // Given
        List<String> mockChunks = generateMockChunks(chunkCount);
        when(ragService.ragQuery(any(RAGQueryRequest.class)))
                .thenReturn(Flux.fromIterable(mockChunks));
        
        // When
        Flux<ServerSentEvent<String>> result = streamController.streamRAGQuery(request);
        
        // Then
        List<ServerSentEvent<String>> events = new ArrayList<>();
        StepVerifier.create(result)
                .recordWith(() -> events)
                .expectNextCount(chunkCount + 1)
                .expectComplete()
                .verify(Duration.ofSeconds(10));
        
        // 过滤掉心跳消息
        List<ServerSentEvent<String>> nonHeartbeatEvents = events.stream()
                .filter(event -> !"heartbeat".equals(event.event()))
                .toList();
        
        // 验证最后一个非心跳事件是完成标记
        assertThat(nonHeartbeatEvents).isNotEmpty();
        ServerSentEvent<String> lastEvent = nonHeartbeatEvents.get(nonHeartbeatEvents.size() - 1);
        assertThat(lastEvent.event()).isEqualTo("done");
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
