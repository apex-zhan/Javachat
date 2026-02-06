package com.abin.mallchat.ai.rag.integration;

import com.abin.mallchat.ai.common.domain.entity.DocumentChunk;
import com.abin.mallchat.ai.common.domain.entity.KnowledgeDocument;
import com.abin.mallchat.ai.common.domain.enums.IndexStatus;
import com.abin.mallchat.ai.rag.domain.dto.DocumentUploadRequest;
import com.abin.mallchat.ai.rag.domain.dto.RAGQueryRequest;
import com.abin.mallchat.ai.rag.service.RAGService;
import com.abin.mallchat.ai.vector.service.VectorService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 端到端 RAG 工作流测试
 * 
 * 测试完整的 RAG 流程：
 * 1. 上传文档
 * 2. 等待索引完成
 * 3. 执行 RAG 查询
 * 4. 验证流式响应
 * 5. 验证数据持久化
 * 
 * @author zxw
 * @since 2025-01-08
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("端到端 RAG 工作流测试")
public class EndToEndRAGWorkflowTest {

    @Autowired
    private RAGService ragService;

    @Autowired
    private VectorService vectorService;

    /**
     * 测试完整的 RAG 工作流
     * 
     * 场景：
     * 1. 上传一个包含技术文档的文本文件
     * 2. 等待异步索引完成
     * 3. 执行相关问题查询
     * 4. 验证返回的答案包含文档内容
     */
    @Test
    @DisplayName("完整 RAG 工作流 - 上传文档 → 索引 → 查询 → 获取答案")
    void testCompleteRAGWorkflow() throws InterruptedException {
        // Given: 准备测试文档
        String documentContent = "Spring Boot 是一个基于 Spring 框架的开源 Java 应用程序框架。" +
                "它简化了 Spring 应用的初始搭建以及开发过程。" +
                "Spring Boot 提供了自动配置功能，可以根据项目依赖自动配置 Spring 应用。" +
                "它还内置了 Tomcat、Jetty 等 Web 服务器，可以直接运行独立的应用程序。";

        DocumentUploadRequest uploadRequest = DocumentUploadRequest.builder()
                .title("Spring Boot 技术文档")
                .content(documentContent)
                .documentType("txt")
                .uploadUserId(1001L)
                .build();

        // When: 上传文档
        log.info("步骤 1: 上传文档");
        Long documentId = ragService.uploadDocument(uploadRequest);
        assertThat(documentId).isNotNull().isPositive();
        log.info("文档上传成功，documentId: {}", documentId);

        // Then: 等待索引完成（最多等待 30 秒）
        log.info("步骤 2: 等待索引完成");
        boolean indexReady = waitForIndexReady(documentId, Duration.ofSeconds(30));
        assertThat(indexReady).isTrue();
        log.info("文档索引完成");

        // When: 执行 RAG 查询
        log.info("步骤 3: 执行 RAG 查询");
        RAGQueryRequest queryRequest = RAGQueryRequest.builder()
                .question("Spring Boot 有什么特点？")
                .documentId(documentId)
                .userId(1001L)
                .build();

        Flux<String> responseStream = ragService.ragQuery(queryRequest);

        // Then: 验证流式响应
        log.info("步骤 4: 验证流式响应");
        AtomicInteger chunkCount = new AtomicInteger(0);
        StringBuilder fullResponse = new StringBuilder();

        StepVerifier.create(responseStream)
                .thenConsumeWhile(
                        chunk -> {
                            chunkCount.incrementAndGet();
                            fullResponse.append(chunk);
                            log.debug("接收到响应块 {}: {}", chunkCount.get(), chunk);
                            return true;
                        }
                )
                .expectComplete()
                .verify(Duration.ofSeconds(30));

        // Then: 验证响应内容
        log.info("步骤 5: 验证响应内容");
        String response = fullResponse.toString();
        log.info("完整响应: {}", response);
        
        assertThat(chunkCount.get()).isGreaterThan(0);
        assertThat(response).isNotEmpty();
        // 响应应该包含文档中的关键信息
        assertThat(response.toLowerCase()).containsAnyOf(
                "spring boot", "自动配置", "简化", "框架"
        );

        log.info("端到端测试完成，共接收 {} 个响应块", chunkCount.get());
    }

    /**
     * 测试多文档 RAG 查询
     */
    @Test
    @DisplayName("多文档 RAG 查询 - 上传多个文档并查询")
    void testMultiDocumentRAGQuery() throws InterruptedException {
        // Given: 上传多个相关文档
        log.info("上传第一个文档：Spring Boot 基础");
        Long doc1Id = uploadAndWaitForIndex(
                "Spring Boot 基础",
                "Spring Boot 是一个快速开发框架，提供了自动配置和起步依赖。",
                1001L
        );

        log.info("上传第二个文档：Spring Boot 高级特性");
        Long doc2Id = uploadAndWaitForIndex(
                "Spring Boot 高级特性",
                "Spring Boot Actuator 提供了生产就绪的功能，如健康检查和指标监控。",
                1001L
        );

        // When: 查询第一个文档
        log.info("查询第一个文档");
        RAGQueryRequest query1 = RAGQueryRequest.builder()
                .question("Spring Boot 的主要特点是什么？")
                .documentId(doc1Id)
                .userId(1001L)
                .build();

        Flux<String> response1 = ragService.ragQuery(query1);
        String answer1 = collectResponse(response1);
        
        assertThat(answer1).containsIgnoringCase("自动配置");

        // When: 查询第二个文档
        log.info("查询第二个文档");
        RAGQueryRequest query2 = RAGQueryRequest.builder()
                .question("Spring Boot 有哪些监控功能？")
                .documentId(doc2Id)
                .userId(1001L)
                .build();

        Flux<String> response2 = ragService.ragQuery(query2);
        String answer2 = collectResponse(response2);
        
        assertThat(answer2).containsIgnoringCase("actuator");

        log.info("多文档查询测试完成");
    }

    /**
     * 测试文档更新后的查询
     */
    @Test
    @DisplayName("文档更新工作流 - 更新文档后查询应返回新内容")
    void testDocumentUpdateWorkflow() throws InterruptedException {
        // Given: 上传初始文档
        log.info("上传初始文档");
        Long documentId = uploadAndWaitForIndex(
                "版本控制系统",
                "Git 是一个分布式版本控制系统，由 Linus Torvalds 创建。",
                1001L
        );

        // When: 查询初始内容
        log.info("查询初始内容");
        RAGQueryRequest query1 = RAGQueryRequest.builder()
                .question("Git 是谁创建的？")
                .documentId(documentId)
                .userId(1001L)
                .build();

        String answer1 = collectResponse(ragService.ragQuery(query1));
        assertThat(answer1).containsIgnoringCase("linus");

        // When: 更新文档
        log.info("更新文档内容");
        DocumentUploadRequest updateRequest = DocumentUploadRequest.builder()
                .title("版本控制系统")
                .content("Git 是一个分布式版本控制系统，支持分支管理和合并操作。GitHub 是基于 Git 的代码托管平台。")
                .documentType("txt")
                .uploadUserId(1001L)
                .build();

        ragService.updateDocument(documentId, updateRequest);
        waitForIndexReady(documentId, Duration.ofSeconds(30));

        // Then: 查询更新后的内容
        log.info("查询更新后的内容");
        RAGQueryRequest query2 = RAGQueryRequest.builder()
                .question("GitHub 是什么？")
                .documentId(documentId)
                .userId(1001L)
                .build();

        String answer2 = collectResponse(ragService.ragQuery(query2));
        assertThat(answer2).containsIgnoringCase("github");

        log.info("文档更新工作流测试完成");
    }

    /**
     * 测试文档删除后的查询
     */
    @Test
    @DisplayName("文档删除工作流 - 删除文档后查询应降级")
    void testDocumentDeletionWorkflow() throws InterruptedException {
        // Given: 上传文档
        log.info("上传文档");
        Long documentId = uploadAndWaitForIndex(
                "测试文档",
                "这是一个用于测试删除功能的文档。",
                1001L
        );

        // When: 删除文档
        log.info("删除文档");
        ragService.deleteDocument(documentId);

        // Then: 查询应该失败或降级
        log.info("验证删除后的查询行为");
        RAGQueryRequest query = RAGQueryRequest.builder()
                .question("这个文档的内容是什么？")
                .documentId(documentId)
                .userId(1001L)
                .build();

        // 应该抛出异常或返回降级响应
        StepVerifier.create(ragService.ragQuery(query))
                .expectErrorMatches(throwable -> 
                        throwable.getMessage().contains("文档不存在") ||
                        throwable.getMessage().contains("索引未就绪")
                )
                .verify(Duration.ofSeconds(10));

        log.info("文档删除工作流测试完成");
    }

    /**
     * 测试并发查询
     */
    @Test
    @DisplayName("并发查询测试 - 多个用户同时查询同一文档")
    void testConcurrentQueries() throws InterruptedException {
        // Given: 上传文档
        log.info("上传文档");
        Long documentId = uploadAndWaitForIndex(
                "并发测试文档",
                "这是一个用于测试并发查询的文档。包含了关于并发、性能和可扩展性的内容。",
                1001L
        );

        // When: 并发执行多个查询
        log.info("执行并发查询");
        int concurrentUsers = 5;
        Flux<String>[] responses = new Flux[concurrentUsers];

        for (int i = 0; i < concurrentUsers; i++) {
            RAGQueryRequest query = RAGQueryRequest.builder()
                    .question("这个文档讲了什么？")
                    .documentId(documentId)
                    .userId(1001L + i)
                    .build();
            responses[i] = ragService.ragQuery(query);
        }

        // Then: 所有查询都应该成功完成
        for (int i = 0; i < concurrentUsers; i++) {
            final int userId = i;
            StepVerifier.create(responses[i])
                    .thenConsumeWhile(chunk -> {
                        log.debug("用户 {} 接收到响应块: {}", userId, chunk);
                        return true;
                    })
                    .expectComplete()
                    .verify(Duration.ofSeconds(30));
        }

        log.info("并发查询测试完成");
    }

    /**
     * 测试流式响应的首字延迟
     */
    @Test
    @DisplayName("首字延迟测试 - 验证流式响应的首个块快速返回")
    void testTimeToFirstByte() throws InterruptedException {
        // Given: 上传文档
        Long documentId = uploadAndWaitForIndex(
                "延迟测试文档",
                "这是一个用于测试响应延迟的文档。",
                1001L
        );

        // When: 执行查询并测量首字延迟
        RAGQueryRequest query = RAGQueryRequest.builder()
                .question("这个文档的主题是什么？")
                .documentId(documentId)
                .userId(1001L)
                .build();

        long startTime = System.currentTimeMillis();
        AtomicInteger firstChunkTime = new AtomicInteger(-1);

        StepVerifier.create(ragService.ragQuery(query))
                .thenConsumeWhile(chunk -> {
                    if (firstChunkTime.get() == -1) {
                        firstChunkTime.set((int) (System.currentTimeMillis() - startTime));
                        log.info("首字延迟: {} ms", firstChunkTime.get());
                    }
                    return true;
                })
                .expectComplete()
                .verify(Duration.ofSeconds(30));

        // Then: 首字延迟应该小于 500ms（根据设计文档要求）
        assertThat(firstChunkTime.get()).isLessThan(500);
        log.info("首字延迟测试通过: {} ms", firstChunkTime.get());
    }

    // ==================== 辅助方法 ====================

    /**
     * 等待文档索引完成
     */
    private boolean waitForIndexReady(Long documentId, Duration timeout) throws InterruptedException {
        long endTime = System.currentTimeMillis() + timeout.toMillis();
        
        while (System.currentTimeMillis() < endTime) {
            IndexStatus status = ragService.checkIndexStatus(documentId);
            log.debug("文档 {} 索引状态: {}", documentId, status);
            
            if (status == IndexStatus.COMPLETED) {
                return true;
            } else if (status == IndexStatus.FAILED) {
                log.error("文档 {} 索引失败", documentId);
                return false;
            }
            
            Thread.sleep(1000); // 每秒检查一次
        }
        
        log.warn("文档 {} 索引超时", documentId);
        return false;
    }

    /**
     * 上传文档并等待索引完成
     */
    private Long uploadAndWaitForIndex(String title, String content, Long userId) throws InterruptedException {
        DocumentUploadRequest request = DocumentUploadRequest.builder()
                .title(title)
                .content(content)
                .documentType("txt")
                .uploadUserId(userId)
                .build();

        Long documentId = ragService.uploadDocument(request);
        boolean ready = waitForIndexReady(documentId, Duration.ofSeconds(30));
        
        if (!ready) {
            throw new RuntimeException("文档索引失败: " + documentId);
        }
        
        return documentId;
    }

    /**
     * 收集流式响应为完整字符串
     */
    private String collectResponse(Flux<String> responseStream) {
        StringBuilder response = new StringBuilder();
        
        StepVerifier.create(responseStream)
                .thenConsumeWhile(chunk -> {
                    response.append(chunk);
                    return true;
                })
                .expectComplete()
                .verify(Duration.ofSeconds(30));
        
        return response.toString();
    }
}
