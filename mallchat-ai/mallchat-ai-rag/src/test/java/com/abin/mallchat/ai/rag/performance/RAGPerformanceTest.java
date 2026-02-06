package com.abin.mallchat.ai.rag.performance;

import com.abin.mallchat.ai.rag.domain.dto.DocumentUploadRequest;
import com.abin.mallchat.ai.rag.domain.dto.RAGQueryRequest;
import com.abin.mallchat.ai.rag.service.RAGService;
import com.abin.mallchat.ai.vector.domain.SearchResult;
import com.abin.mallchat.ai.vector.service.VectorService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RAG 系统性能测试
 * 
 * 测试指标：
 * 1. 首字延迟 (TTFB) - 目标: < 500ms
 * 2. 向量检索延迟 - 目标: < 100ms
 * 3. 文档索引速度 - 目标: > 10 docs/min
 * 4. 并发查询 QPS - 目标: > 100
 * 5. 流式输出延迟 - 目标: < 50ms/chunk
 * 
 * @author zxw
 * @since 2025-01-08
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("RAG 系统性能测试")
public class RAGPerformanceTest {

    @Autowired
    private RAGService ragService;

    @Autowired
    private VectorService vectorService;

    private Long testDocumentId;

    @BeforeAll
    void setup() throws InterruptedException {
        log.info("=".repeat(80));
        log.info("开始性能测试准备工作");
        log.info("=".repeat(80));

        // 准备测试文档
        String content = generateLargeDocument(5000); // 5000 字的文档
        DocumentUploadRequest request = DocumentUploadRequest.builder()
                .title("性能测试文档")
                .content(content)
                .documentType("txt")
                .uploadUserId(9999L)
                .build();

        testDocumentId = ragService.uploadDocument(request);
        log.info("测试文档已上传，ID: {}", testDocumentId);

        // 等待索引完成
        waitForIndexReady(testDocumentId, Duration.ofMinutes(2));
        log.info("测试文档索引完成");
        log.info("=".repeat(80));
    }

    /**
     * 性能测试 1: 首字延迟 (Time To First Byte)
     * 
     * 目标: < 500ms
     * 测试方法: 执行 10 次查询，测量每次的首字延迟
     */
    @Test
    @DisplayName("性能测试 1: 首字延迟 (TTFB) - 目标 < 500ms")
    void testTimeToFirstByte() {
        log.info("\n" + "=".repeat(80));
        log.info("性能测试 1: 首字延迟 (TTFB)");
        log.info("=".repeat(80));

        int iterations = 10;
        List<Long> ttfbList = new ArrayList<>();

        for (int i = 0; i < iterations; i++) {
            RAGQueryRequest query = RAGQueryRequest.builder()
                    .question("这个文档的主要内容是什么？")
                    .documentId(testDocumentId)
                    .userId(9999L)
                    .build();

            long startTime = System.currentTimeMillis();
            AtomicLong firstChunkTime = new AtomicLong(-1);

            StepVerifier.create(ragService.ragQuery(query))
                    .thenConsumeWhile(chunk -> {
                        if (firstChunkTime.get() == -1) {
                            firstChunkTime.set(System.currentTimeMillis() - startTime);
                        }
                        return true;
                    })
                    .expectComplete()
                    .verify(Duration.ofSeconds(30));

            ttfbList.add(firstChunkTime.get());
            log.info("第 {} 次查询 TTFB: {} ms", i + 1, firstChunkTime.get());
        }

        // 计算统计数据
        double avgTTFB = ttfbList.stream().mapToLong(Long::longValue).average().orElse(0);
        long minTTFB = ttfbList.stream().mapToLong(Long::longValue).min().orElse(0);
        long maxTTFB = ttfbList.stream().mapToLong(Long::longValue).max().orElse(0);

        log.info("-".repeat(80));
        log.info("TTFB 统计结果:");
        log.info("  平均值: {} ms", String.format("%.2f", avgTTFB));
        log.info("  最小值: {} ms", minTTFB);
        log.info("  最大值: {} ms", maxTTFB);
        log.info("  目标值: < 500 ms");
        log.info("  测试结果: {}", avgTTFB < 500 ? "✅ 通过" : "❌ 未通过");
        log.info("=".repeat(80));

        assertThat(avgTTFB).isLessThan(500);
    }

    /**
     * 性能测试 2: 向量检索延迟
     * 
     * 目标: < 100ms
     * 测试方法: 执行 50 次向量检索，测量每次的延迟
     */
    @Test
    @DisplayName("性能测试 2: 向量检索延迟 - 目标 < 100ms")
    void testVectorSearchLatency() {
        log.info("\n" + "=".repeat(80));
        log.info("性能测试 2: 向量检索延迟");
        log.info("=".repeat(80));

        int iterations = 50;
        List<Long> latencyList = new ArrayList<>();

        // 生成测试查询向量
        String testQuery = "测试查询内容";
        float[] queryVector = vectorService.generateEmbedding(testQuery);

        for (int i = 0; i < iterations; i++) {
            long startTime = System.nanoTime();
            List<SearchResult> results = vectorService.search(queryVector, 5, testDocumentId);
            long endTime = System.nanoTime();

            long latencyMs = (endTime - startTime) / 1_000_000;
            latencyList.add(latencyMs);

            if (i % 10 == 0) {
                log.info("第 {} 次检索延迟: {} ms, 结果数: {}", i + 1, latencyMs, results.size());
            }
        }

        // 计算统计数据
        double avgLatency = latencyList.stream().mapToLong(Long::longValue).average().orElse(0);
        long minLatency = latencyList.stream().mapToLong(Long::longValue).min().orElse(0);
        long maxLatency = latencyList.stream().mapToLong(Long::longValue).max().orElse(0);
        long p95Latency = calculatePercentile(latencyList, 95);
        long p99Latency = calculatePercentile(latencyList, 99);

        log.info("-".repeat(80));
        log.info("向量检索延迟统计结果:");
        log.info("  平均值: {} ms", String.format("%.2f", avgLatency));
        log.info("  最小值: {} ms", minLatency);
        log.info("  最大值: {} ms", maxLatency);
        log.info("  P95: {} ms", p95Latency);
        log.info("  P99: {} ms", p99Latency);
        log.info("  目标值: < 100 ms");
        log.info("  测试结果: {}", avgLatency < 100 ? "✅ 通过" : "❌ 未通过");
        log.info("=".repeat(80));

        assertThat(avgLatency).isLessThan(100);
    }

    /**
     * 性能测试 3: 文档索引速度
     * 
     * 目标: > 10 docs/min
     * 测试方法: 批量上传 20 个文档，测量总耗时
     */
    @Test
    @DisplayName("性能测试 3: 文档索引速度 - 目标 > 10 docs/min")
    void testDocumentIndexingSpeed() throws InterruptedException {
        log.info("\n" + "=".repeat(80));
        log.info("性能测试 3: 文档索引速度");
        log.info("=".repeat(80));

        int documentCount = 20;
        List<Long> documentIds = new ArrayList<>();

        long startTime = System.currentTimeMillis();

        // 批量上传文档
        for (int i = 0; i < documentCount; i++) {
            String content = generateLargeDocument(1000); // 1000 字的文档
            DocumentUploadRequest request = DocumentUploadRequest.builder()
                    .title("索引测试文档 " + i)
                    .content(content)
                    .documentType("txt")
                    .uploadUserId(9999L)
                    .build();

            Long docId = ragService.uploadDocument(request);
            documentIds.add(docId);
            log.info("已上传文档 {}/{}", i + 1, documentCount);
        }

        // 等待所有文档索引完成
        log.info("等待所有文档索引完成...");
        for (Long docId : documentIds) {
            waitForIndexReady(docId, Duration.ofMinutes(5));
        }

        long endTime = System.currentTimeMillis();
        long totalTimeMs = endTime - startTime;
        double totalTimeMin = totalTimeMs / 60000.0;
        double docsPerMin = documentCount / totalTimeMin;

        log.info("-".repeat(80));
        log.info("文档索引速度统计结果:");
        log.info("  文档数量: {}", documentCount);
        log.info("  总耗时: {} ms ({} 分钟)", totalTimeMs, String.format("%.2f", totalTimeMin));
        log.info("  索引速度: {} docs/min", String.format("%.2f", docsPerMin));
        log.info("  目标值: > 10 docs/min");
        log.info("  测试结果: {}", docsPerMin > 10 ? "✅ 通过" : "❌ 未通过");
        log.info("=".repeat(80));

        assertThat(docsPerMin).isGreaterThan(10);
    }

    /**
     * 性能测试 4: 并发查询 QPS
     * 
     * 目标: > 100 QPS
     * 测试方法: 使用 10 个线程并发执行查询，持续 10 秒
     */
    @Test
    @DisplayName("性能测试 4: 并发查询 QPS - 目标 > 100")
    void testConcurrentQueryQPS() throws InterruptedException {
        log.info("\n" + "=".repeat(80));
        log.info("性能测试 4: 并发查询 QPS");
        log.info("=".repeat(80));

        int threadCount = 10;
        int durationSeconds = 10;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        long startTime = System.currentTimeMillis();

        // 启动并发查询
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    long endTime = System.currentTimeMillis() + (durationSeconds * 1000);
                    
                    while (System.currentTimeMillis() < endTime) {
                        try {
                            RAGQueryRequest query = RAGQueryRequest.builder()
                                    .question("这个文档讲了什么？")
                                    .documentId(testDocumentId)
                                    .userId(9999L + threadId)
                                    .build();

                            StepVerifier.create(ragService.ragQuery(query))
                                    .thenConsumeWhile(chunk -> true)
                                    .expectComplete()
                                    .verify(Duration.ofSeconds(30));

                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                            log.warn("线程 {} 查询失败: {}", threadId, e.getMessage());
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // 等待所有线程完成
        latch.await(durationSeconds + 5, TimeUnit.SECONDS);
        executor.shutdown();

        long totalTimeMs = System.currentTimeMillis() - startTime;
        double totalTimeSec = totalTimeMs / 1000.0;
        double qps = successCount.get() / totalTimeSec;

        log.info("-".repeat(80));
        log.info("并发查询 QPS 统计结果:");
        log.info("  并发线程数: {}", threadCount);
        log.info("  测试时长: {} 秒", String.format("%.2f", totalTimeSec));
        log.info("  成功请求数: {}", successCount.get());
        log.info("  失败请求数: {}", errorCount.get());
        log.info("  QPS: {}", String.format("%.2f", qps));
        log.info("  目标值: > 100 QPS");
        log.info("  测试结果: {}", qps > 100 ? "✅ 通过" : "❌ 未通过");
        log.info("=".repeat(80));

        assertThat(qps).isGreaterThan(100);
    }

    /**
     * 性能测试 5: 流式输出延迟
     * 
     * 目标: < 50ms/chunk
     * 测试方法: 执行查询并测量相邻块之间的时间间隔
     */
    @Test
    @DisplayName("性能测试 5: 流式输出延迟 - 目标 < 50ms/chunk")
    void testStreamingChunkLatency() {
        log.info("\n" + "=".repeat(80));
        log.info("性能测试 5: 流式输出延迟");
        log.info("=".repeat(80));

        RAGQueryRequest query = RAGQueryRequest.builder()
                .question("请详细介绍这个文档的内容。")
                .documentId(testDocumentId)
                .userId(9999L)
                .build();

        List<Long> chunkIntervals = new ArrayList<>();
        AtomicLong lastChunkTime = new AtomicLong(System.currentTimeMillis());

        StepVerifier.create(ragService.ragQuery(query))
                .thenConsumeWhile(chunk -> {
                    long currentTime = System.currentTimeMillis();
                    long interval = currentTime - lastChunkTime.get();
                    
                    if (lastChunkTime.get() > 0) {
                        chunkIntervals.add(interval);
                    }
                    
                    lastChunkTime.set(currentTime);
                    return true;
                })
                .expectComplete()
                .verify(Duration.ofSeconds(30));

        // 计算统计数据
        if (!chunkIntervals.isEmpty()) {
            double avgInterval = chunkIntervals.stream().mapToLong(Long::longValue).average().orElse(0);
            long minInterval = chunkIntervals.stream().mapToLong(Long::longValue).min().orElse(0);
            long maxInterval = chunkIntervals.stream().mapToLong(Long::longValue).max().orElse(0);

            log.info("-".repeat(80));
            log.info("流式输出延迟统计结果:");
            log.info("  总块数: {}", chunkIntervals.size() + 1);
            log.info("  平均间隔: {} ms", String.format("%.2f", avgInterval));
            log.info("  最小间隔: {} ms", minInterval);
            log.info("  最大间隔: {} ms", maxInterval);
            log.info("  目标值: < 50 ms/chunk");
            log.info("  测试结果: {}", avgInterval < 50 ? "✅ 通过" : "❌ 未通过");
            log.info("=".repeat(80));

            assertThat(avgInterval).isLessThan(50);
        } else {
            log.warn("未收集到足够的块间隔数据");
        }
    }

    /**
     * 综合性能报告
     */
    @Test
    @DisplayName("综合性能报告")
    void generatePerformanceReport() {
        log.info("\n" + "=".repeat(80));
        log.info("RAG 系统性能测试综合报告");
        log.info("=".repeat(80));
        log.info("");
        log.info("请依次运行以下性能测试：");
        log.info("  1. testTimeToFirstByte() - 首字延迟测试");
        log.info("  2. testVectorSearchLatency() - 向量检索延迟测试");
        log.info("  3. testDocumentIndexingSpeed() - 文档索引速度测试");
        log.info("  4. testConcurrentQueryQPS() - 并发查询 QPS 测试");
        log.info("  5. testStreamingChunkLatency() - 流式输出延迟测试");
        log.info("");
        log.info("性能目标：");
        log.info("  ✓ 首字延迟 (TTFB): < 500ms");
        log.info("  ✓ 向量检索延迟: < 100ms");
        log.info("  ✓ 文档索引速度: > 10 docs/min");
        log.info("  ✓ 并发查询 QPS: > 100");
        log.info("  ✓ 流式输出延迟: < 50ms/chunk");
        log.info("");
        log.info("=".repeat(80));
    }

    // ==================== 辅助方法 ====================

    private String generateLargeDocument(int wordCount) {
        StringBuilder sb = new StringBuilder();
        String[] sentences = {
                "Spring Boot 是一个基于 Spring 框架的开源 Java 应用程序框架。",
                "它简化了 Spring 应用的初始搭建以及开发过程。",
                "Spring Boot 提供了自动配置功能，可以根据项目依赖自动配置 Spring 应用。",
                "它还内置了 Tomcat、Jetty 等 Web 服务器，可以直接运行独立的应用程序。",
                "Spring Boot Actuator 提供了生产就绪的功能，如健康检查和指标监控。"
        };

        int currentWords = 0;
        int sentenceIndex = 0;

        while (currentWords < wordCount) {
            sb.append(sentences[sentenceIndex % sentences.length]).append(" ");
            currentWords += sentences[sentenceIndex % sentences.length].length();
            sentenceIndex++;
        }

        return sb.toString();
    }

    private void waitForIndexReady(Long documentId, Duration timeout) throws InterruptedException {
        long endTime = System.currentTimeMillis() + timeout.toMillis();
        
        while (System.currentTimeMillis() < endTime) {
            try {
                var status = ragService.checkIndexStatus(documentId);
                if (status.name().equals("COMPLETED")) {
                    return;
                }
            } catch (Exception e) {
                // 忽略异常，继续等待
            }
            Thread.sleep(1000);
        }
        
        throw new RuntimeException("文档索引超时: " + documentId);
    }

    private long calculatePercentile(List<Long> values, int percentile) {
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compareTo);
        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, index));
    }
}
