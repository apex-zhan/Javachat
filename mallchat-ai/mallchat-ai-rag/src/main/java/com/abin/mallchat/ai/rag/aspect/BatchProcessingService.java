package com.abin.mallchat.ai.rag.aspect;

import com.abin.mallchat.ai.common.domain.entity.DocumentChunk;
import com.abin.mallchat.ai.vector.service.EmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * 批量处理服务
 * <p>
 * 功能：
 * 1. 批量向量生成（减少API调用次数）
 * 2. 批量数据库操作（提升写入性能）
 * 3. 并发处理优化（充分利用多核CPU）
 * <p>
 * 优化策略：
 * - 向量生成：批量调用API，一次生成多个向量
 * - 数据库操作：使用批量插入，减少网络往返
 * - 并发控制：使用线程池并发处理大批量数据
 *
 * @author zxw
 */
@Slf4j
@Service
public class BatchProcessingService {

    @Autowired
    private EmbeddingService embeddingService;

    /**
     * 批量处理的默认大小
     * 根据API限制和性能测试结果调整
     */
    @Value("${ai.batch.embedding.size:100}")
    private int embeddingBatchSize;

    @Value("${ai.batch.database.size:500}")
    private int databaseBatchSize;

    @Value("${ai.batch.concurrent.threads:4}")
    private int concurrentThreads;

    private ExecutorService executorService;

    /**
     * 初始化线程池
     */
    public void init() {
        this.executorService = new ThreadPoolExecutor(
                concurrentThreads,
                concurrentThreads * 2,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        log.info("批量处理服务初始化完成，线程数：{}", concurrentThreads);
    }

    /**
     * 批量生成向量（优化版本）
     * <p>
     * 将大批量数据分成多个小批次，每个批次使用批量API生成向量
     * 这样可以在保证性能的同时，避免单次请求数据量过大
     *
     * @param texts 文本列表
     * @return 向量列表
     */
    public List<float[]> batchGenerateEmbeddings(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }

        int totalSize = texts.size();
        log.info("开始批量生成向量，总数量：{}, 批次大小：{}", totalSize, embeddingBatchSize);

        List<float[]> allEmbeddings = new ArrayList<>(totalSize);

        // 分批处理
        for (int i = 0; i < totalSize; i += embeddingBatchSize) {
            int endIndex = Math.min(i + embeddingBatchSize, totalSize);
            List<String> batch = texts.subList(i, endIndex);

            log.debug("处理批次 {}/{}, 大小：{}",
                    (i / embeddingBatchSize) + 1,
                    (totalSize + embeddingBatchSize - 1) / embeddingBatchSize,
                    batch.size());

            try {
                // 批量生成向量
                List<float[]> batchEmbeddings = embeddingService.generateEmbeddings(batch);
                allEmbeddings.addAll(batchEmbeddings);

            } catch (Exception e) {
                log.error("批次向量生成失败，批次索引：{}", i, e);
                throw new RuntimeException("批量向量生成失败", e);
            }
        }

        log.info("批量向量生成完成，总数量：{}", allEmbeddings.size());
        return allEmbeddings;
    }

    /**
     * 并发批量生成向量（高性能版本）
     * <p>
     * 使用线程池并发处理多个批次，进一步提升性能
     * 适用于大规模数据处理场景
     *
     * @param texts 文本列表
     * @return 向量列表
     */
    public List<float[]> concurrentBatchGenerateEmbeddings(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }

        int totalSize = texts.size();
        log.info("开始并发批量生成向量，总数量：{}, 批次大小：{}, 并发线程：{}",
                totalSize, embeddingBatchSize, concurrentThreads);

        // 分批
        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < totalSize; i += embeddingBatchSize) {
            int endIndex = Math.min(i + embeddingBatchSize, totalSize);
            batches.add(texts.subList(i, endIndex));
        }

        // 并发处理
        List<Future<List<float[]>>> futures = new ArrayList<>();
        for (int i = 0; i < batches.size(); i++) {
            final int batchIndex = i;
            final List<String> batch = batches.get(i);

            Future<List<float[]>> future = executorService.submit(() -> {
                log.debug("并发处理批次 {}/{}", batchIndex + 1, batches.size());
                return embeddingService.generateEmbeddings(batch);
            });

            futures.add(future);
        }

        // 收集结果
        List<float[]> allEmbeddings = new ArrayList<>(totalSize);
        for (int i = 0; i < futures.size(); i++) {
            try {
                List<float[]> batchEmbeddings = futures.get(i).get(60, TimeUnit.SECONDS);
                allEmbeddings.addAll(batchEmbeddings);
            } catch (Exception e) {
                log.error("获取批次结果失败，批次索引：{}", i, e);
                throw new RuntimeException("并发批量向量生成失败", e);
            }
        }

        log.info("并发批量向量生成完成，总数量：{}", allEmbeddings.size());
        return allEmbeddings;
    }

    /**
     * 批量处理文档分块
     * <p>
     * 将大量分块分成多个批次处理，避免内存溢出
     *
     * @param chunks    文档分块列表
     * @param processor 处理器函数
     * @param <T>       返回类型
     * @return 处理结果列表
     */
    public <T> List<T> batchProcessChunks(
            List<DocumentChunk> chunks,
            ChunkProcessor<T> processor) {

        if (chunks == null || chunks.isEmpty()) {
            return new ArrayList<>();
        }

        int totalSize = chunks.size();
        log.info("开始批量处理分块，总数量：{}, 批次大小：{}", totalSize, databaseBatchSize);

        List<T> allResults = new ArrayList<>(totalSize);

        // 分批处理
        for (int i = 0; i < totalSize; i += databaseBatchSize) {
            int endIndex = Math.min(i + databaseBatchSize, totalSize);
            List<DocumentChunk> batch = chunks.subList(i, endIndex);

            log.debug("处理批次 {}/{}, 大小：{}",
                    (i / databaseBatchSize) + 1,
                    (totalSize + databaseBatchSize - 1) / databaseBatchSize,
                    batch.size());

            try {
                List<T> batchResults = processor.process(batch);
                allResults.addAll(batchResults);

            } catch (Exception e) {
                log.error("批次处理失败，批次索引：{}", i, e);
                throw new RuntimeException("批量处理失败", e);
            }
        }

        log.info("批量处理完成，总数量：{}", allResults.size());
        return allResults;
    }

    /**
     * 分块处理器接口
     */
    @FunctionalInterface
    public interface ChunkProcessor<T> {
        List<T> process(List<DocumentChunk> chunks) throws Exception;
    }

    /**
     * 获取推荐的批次大小
     * <p>
     * 根据数据量动态调整批次大小
     *
     * @param totalSize     总数据量
     * @param operationType 操作类型（embedding, database）
     * @return 推荐的批次大小
     */
    public int getRecommendedBatchSize(int totalSize, String operationType) {
        if ("embedding".equals(operationType)) {
            // 向量生成：小批量，避免API超时
            if (totalSize < 50) {
                return totalSize;
            } else if (totalSize < 500) {
                return 50;
            } else {
                return embeddingBatchSize;
            }
        } else if ("database".equals(operationType)) {
            // 数据库操作：大批量，提升写入性能
            if (totalSize < 100) {
                return totalSize;
            } else if (totalSize < 1000) {
                return 100;
            } else {
                return databaseBatchSize;
            }
        }

        return 100; // 默认值
    }

    /**
     * 关闭线程池
     */
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            log.info("关闭批量处理线程池");
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
