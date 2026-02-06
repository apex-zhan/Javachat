package com.abin.mallchat.ai.rag.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 向量检索指标记录服务
 * 使用 Micrometer 记录向量检索的关键指标
 * 
 * @author AI Assistant
 * @date 2025-01-07
 */
@Slf4j
@Component
public class VectorSearchMetrics {

    private final MeterRegistry meterRegistry;
    
    // 计数器
    private final Counter searchTotalCounter;
    private final Counter searchHitCounter;
    private final Counter searchMissCounter;
    
    // 计时器
    private final Timer searchTimer;

    public VectorSearchMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // 初始化计数器
        this.searchTotalCounter = Counter.builder("vector.search.total")
                .description("Total number of vector searches")
                .tag("type", "search")
                .register(meterRegistry);
        
        this.searchHitCounter = Counter.builder("vector.search.hit")
                .description("Number of vector searches with results")
                .tag("type", "hit")
                .register(meterRegistry);
        
        this.searchMissCounter = Counter.builder("vector.search.miss")
                .description("Number of vector searches without results")
                .tag("type", "miss")
                .register(meterRegistry);
        
        // 初始化计时器
        this.searchTimer = Timer.builder("vector.search.duration")
                .description("Vector search duration")
                .tag("operation", "search")
                .register(meterRegistry);
    }

    /**
     * 记录向量检索指标
     * 
     * @param queryTime 查询耗时（毫秒）
     * @param numResults 结果数量
     * @param topScore 最高分数
     */
    public void recordSearch(long queryTime, int numResults, Float topScore) {
        // 记录总搜索次数
        searchTotalCounter.increment();
        
        // 记录命中/未命中
        if (numResults > 0) {
            searchHitCounter.increment();
        } else {
            searchMissCounter.increment();
        }
        
        // 记录查询耗时
        searchTimer.record(queryTime, TimeUnit.MILLISECONDS);
        
        // 记录结果数量分布
        meterRegistry.gauge("vector.search.results.count", numResults);
        
        // 记录最高分数
        if (topScore != null) {
            meterRegistry.gauge("vector.search.top.score", topScore);
        }
        
        // 记录命中率
        double hitRate = calculateHitRate();
        meterRegistry.gauge("vector.search.hit.rate", hitRate);
        
        // 日志记录
        log.info("[VECTOR-SEARCH-METRICS] queryTime={}ms, numResults={}, topScore={}, hitRate={}%", 
                queryTime, numResults, topScore, String.format("%.2f", hitRate * 100));
    }

    /**
     * 计算命中率
     */
    private double calculateHitRate() {
        double totalCount = searchTotalCounter.count();
        double hitCount = searchHitCounter.count();
        
        if (totalCount == 0) {
            return 0.0;
        }
        
        return hitCount / totalCount;
    }

    /**
     * 获取平均查询时间（毫秒）
     */
    public double getAverageQueryTime() {
        return searchTimer.mean(TimeUnit.MILLISECONDS);
    }

    /**
     * 获取最大查询时间（毫秒）
     */
    public double getMaxQueryTime() {
        return searchTimer.max(TimeUnit.MILLISECONDS);
    }

    /**
     * 获取总搜索次数
     */
    public double getTotalSearchCount() {
        return searchTotalCounter.count();
    }

    /**
     * 获取命中次数
     */
    public double getHitCount() {
        return searchHitCounter.count();
    }

    /**
     * 获取未命中次数
     */
    public double getMissCount() {
        return searchMissCounter.count();
    }

    /**
     * 获取命中率
     */
    public double getHitRate() {
        return calculateHitRate();
    }
}
