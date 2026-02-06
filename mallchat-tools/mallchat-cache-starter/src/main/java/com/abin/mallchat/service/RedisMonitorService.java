package com.abin.mallchat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis监控告警服务
 * 负责监控Redis状态并发送告警通知
 */
@Service
@Slf4j
public class RedisMonitorService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CircuitBreakerService circuitBreakerService;

    @Autowired
    private LocalCacheService localCacheService;

    @Autowired
    private AlertService alertService;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);

    // 监控指标
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);
    private final AtomicLong responseTimeSum = new AtomicLong(0);
    private volatile long lastAlertTime = 0;

    // 配置参数
    private static final long ALERT_INTERVAL = 300000; // 5分钟告警间隔
    private static final double ERROR_RATE_THRESHOLD = 0.1; // 10%错误率阈值
    private static final long RESPONSE_TIME_THRESHOLD = 1000; // 1秒响应时间阈值

    @PostConstruct
    public void init() {
        startMonitoring();
        log.info("Redis监控服务启动完成");
    }

    /**
     * 启动监控任务
     */
    private void startMonitoring() {
        // 健康检查监控 - 每30秒
        scheduler.scheduleAtFixedRate(this::healthCheck, 0, 30, TimeUnit.SECONDS);

        // 性能监控 - 每分钟
        scheduler.scheduleAtFixedRate(this::performanceCheck, 60, 60, TimeUnit.SECONDS);

        // 缓存统计监控 - 每5分钟
        scheduler.scheduleAtFixedRate(this::cacheStatsCheck, 300, 300, TimeUnit.SECONDS);
    }

    /**
     * 健康检查
     */
    private void healthCheck() {
        try {
            long startTime = System.currentTimeMillis();

            // 执行ping命令
            String result = stringRedisTemplate.execute((org.springframework.data.redis.core.RedisCallback<String>) connection -> {
                return connection.ping();
            });

            long responseTime = System.currentTimeMillis() - startTime;

            // 记录监控指标
            totalRequests.incrementAndGet();
            responseTimeSum.addAndGet(responseTime);

            if ("PONG".equals(result)) {
                log.debug("Redis健康检查正常，响应时间: {}ms", responseTime);

                // 检查响应时间是否过长
                if (responseTime > RESPONSE_TIME_THRESHOLD) {
                    sendAlert(AlertLevel.WARNING,
                            "Redis响应时间过长",
                            String.format("响应时间: %dms，超过阈值: %dms", responseTime, RESPONSE_TIME_THRESHOLD));
                }
            } else {
                failedRequests.incrementAndGet();
                sendAlert(AlertLevel.ERROR, "Redis健康检查失败", "Ping命令返回异常: " + result);
            }

        } catch (Exception e) {
            failedRequests.incrementAndGet();
            log.error("Redis健康检查异常", e);
            sendAlert(AlertLevel.CRITICAL, "Redis连接异常", e.getMessage());
        }
    }

    /**
     * 性能监控检查
     */
    private void performanceCheck() {
        try {
            long total = totalRequests.get();
            long failed = failedRequests.get();

            if (total > 0) {
                double errorRate = (double) failed / total;
                long avgResponseTime = responseTimeSum.get() / total;

                log.info("Redis性能统计 - 总请求: {}, 失败: {}, 错误率: {:.2%}, 平均响应时间: {}ms",
                        total, failed, errorRate, avgResponseTime);

                // 检查错误率
                if (errorRate > ERROR_RATE_THRESHOLD) {
                    sendAlert(AlertLevel.ERROR,
                            "Redis错误率过高",
                            String.format("错误率: %.2f%%，超过阈值: %.2f%%", errorRate * 100, ERROR_RATE_THRESHOLD * 100));
                }

                // 检查熔断器状态
                CircuitBreakerService.CircuitBreakerStats stats = circuitBreakerService.getStats();
                if (!stats.isHealthy()) {
                    sendAlert(AlertLevel.WARNING,
                            "Redis熔断器异常",
                            String.format("当前状态: %s，失败次数: %d", stats.getState(), stats.getFailureCount()));
                }
            }

            // 重置计数器（可选，根据需要决定是否重置）
            // resetCounters();

        } catch (Exception e) {
            log.error("性能监控检查异常", e);
        }
    }

    /**
     * 内存使用率监控
     */
    public void checkMemoryUsage() {
        try {
            Properties info = stringRedisTemplate.execute((RedisCallback<Properties>)
                    connection -> connection.info("memory"));

            assert info != null;
            long usedMemory = Long.parseLong(info.getProperty("used_memory"));
            long maxMemory = Long.parseLong(info.getProperty("maxmemory"));
            double usagePercent = (double) usedMemory / maxMemory * 100;

            if (usagePercent > 80) {
                sendAlert(AlertLevel.WARNING, "Redis内存使用率过高",
                        String.format("当前使用率: %.2f%%", usagePercent));
            }
        } catch (Exception e) {
            log.error("检查Redis内存使用率失败", e);
        }
    }

    /**
     * 缓存统计检查
     */
    private void cacheStatsCheck() {
        try {
            LocalCacheService.CacheStats stats = localCacheService.getCacheStats();

            log.info("本地缓存统计 - Token缓存: {}个(命中率: {:.2%}), 用户信息缓存: {}个(命中率: {:.2%}), 待同步操作: {}个",
                    stats.getTokenCacheSize(), stats.getTokenCacheHitRate(),
                    stats.getUserInfoCacheSize(), stats.getUserInfoCacheHitRate(),
                    stats.getPendingOperationCount());

            // 检查待同步操作数量
            if (stats.getPendingOperationCount() > 1000) {
                sendAlert(AlertLevel.WARNING,
                        "待同步操作过多",
                        String.format("当前待同步操作: %d个，可能存在Redis连接问题", stats.getPendingOperationCount()));
            }

            // 检查缓存命中率
            if (stats.getTokenCacheHitRate() < 0.5) {
                sendAlert(AlertLevel.INFO,
                        "Token缓存命中率较低",
                        String.format("当前命中率: %.2f%%，建议检查缓存策略", stats.getTokenCacheHitRate() * 100));
            }

        } catch (Exception e) {
            log.error("缓存统计检查异常", e);
        }
    }

    /**
     * 发送告警
     */
    private void sendAlert(AlertLevel level, String title, String message) {
        long currentTime = System.currentTimeMillis();

        // 防止告警风暴，限制告警频率
        if (currentTime - lastAlertTime < ALERT_INTERVAL) {
            return;
        }

        try {
            Alert alert = Alert.builder()
                    .level(level)
                    .title(title)
                    .message(message)
                    .timestamp(currentTime)
                    .source("RedisMonitor")
                    .build();

            alertService.sendAlert(alert);
            lastAlertTime = currentTime;

            log.warn("发送Redis告警 - 级别: {}, 标题: {}, 消息: {}", level, title, message);

        } catch (Exception e) {
            log.error("发送告警失败", e);
        }
    }

    /**
     * 记录请求指标
     */
    public void recordRequest(boolean success, long responseTime) {
        totalRequests.incrementAndGet();
        responseTimeSum.addAndGet(responseTime);

        if (!success) {
            failedRequests.incrementAndGet();
        }
    }

    /**
     * 获取监控统计
     */
    public MonitorStats getMonitorStats() {
        long total = totalRequests.get();
        long failed = failedRequests.get();
        double errorRate = total > 0 ? (double) failed / total : 0;
        long avgResponseTime = total > 0 ? responseTimeSum.get() / total : 0;

        return MonitorStats.builder()
                .totalRequests(total)
                .failedRequests(failed)
                .errorRate(errorRate)
                .avgResponseTime(avgResponseTime)
                .circuitBreakerStats(circuitBreakerService.getStats())
                .cacheStats(localCacheService.getCacheStats())
                .build();
    }

    /**
     * 重置计数器
     */
    public void resetCounters() {
        totalRequests.set(0);
        failedRequests.set(0);
        responseTimeSum.set(0);
        log.info("监控计数器已重置");
    }

    /**
     * 告警级别
     */
    public enum AlertLevel {
        INFO, WARNING, ERROR, CRITICAL
    }

    /**
     * 告警实体
     */
    public static class Alert {
        private final AlertLevel level;
        private final String title;
        private final String message;
        private final long timestamp;
        private final String source;

        private Alert(Builder builder) {
            this.level = builder.level;
            this.title = builder.title;
            this.message = builder.message;
            this.timestamp = builder.timestamp;
            this.source = builder.source;
        }

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public AlertLevel getLevel() {
            return level;
        }

        public String getTitle() {
            return title;
        }

        public String getMessage() {
            return message;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getSource() {
            return source;
        }

        public static class Builder {
            private AlertLevel level;
            private String title;
            private String message;
            private long timestamp;
            private String source;

            public Builder level(AlertLevel level) {
                this.level = level;
                return this;
            }

            public Builder title(String title) {
                this.title = title;
                return this;
            }

            public Builder message(String message) {
                this.message = message;
                return this;
            }

            public Builder timestamp(long timestamp) {
                this.timestamp = timestamp;
                return this;
            }

            public Builder source(String source) {
                this.source = source;
                return this;
            }

            public Alert build() {
                return new Alert(this);
            }
        }
    }

    /**
     * 监控统计信息
     */
    public static class MonitorStats {
        private final long totalRequests;
        private final long failedRequests;
        private final double errorRate;
        private final long avgResponseTime;
        private final CircuitBreakerService.CircuitBreakerStats circuitBreakerStats;
        private final LocalCacheService.CacheStats cacheStats;

        private MonitorStats(Builder builder) {
            this.totalRequests = builder.totalRequests;
            this.failedRequests = builder.failedRequests;
            this.errorRate = builder.errorRate;
            this.avgResponseTime = builder.avgResponseTime;
            this.circuitBreakerStats = builder.circuitBreakerStats;
            this.cacheStats = builder.cacheStats;
        }

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public long getTotalRequests() {
            return totalRequests;
        }

        public long getFailedRequests() {
            return failedRequests;
        }

        public double getErrorRate() {
            return errorRate;
        }

        public long getAvgResponseTime() {
            return avgResponseTime;
        }

        public CircuitBreakerService.CircuitBreakerStats getCircuitBreakerStats() {
            return circuitBreakerStats;
        }

        public LocalCacheService.CacheStats getCacheStats() {
            return cacheStats;
        }

        public static class Builder {
            private long totalRequests;
            private long failedRequests;
            private double errorRate;
            private long avgResponseTime;
            private CircuitBreakerService.CircuitBreakerStats circuitBreakerStats;
            private LocalCacheService.CacheStats cacheStats;

            public Builder totalRequests(long totalRequests) {
                this.totalRequests = totalRequests;
                return this;
            }

            public Builder failedRequests(long failedRequests) {
                this.failedRequests = failedRequests;
                return this;
            }

            public Builder errorRate(double errorRate) {
                this.errorRate = errorRate;
                return this;
            }

            public Builder avgResponseTime(long avgResponseTime) {
                this.avgResponseTime = avgResponseTime;
                return this;
            }

            public Builder circuitBreakerStats(CircuitBreakerService.CircuitBreakerStats circuitBreakerStats) {
                this.circuitBreakerStats = circuitBreakerStats;
                return this;
            }

            public Builder cacheStats(LocalCacheService.CacheStats cacheStats) {
                this.cacheStats = cacheStats;
                return this;
            }

            public MonitorStats build() {
                return new MonitorStats(this);
            }
        }
    }
}