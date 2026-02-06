package com.abin.mallchat.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/**
 * 本地缓存服务
 * 作为Redis的二级缓存，在Redis不可用时提供基础的缓存功能
 */
@Service
@Slf4j
public class LocalCacheService {

    // Token缓存 - 较短过期时间，避免token泄露风险
    private Cache<String, String> tokenCache;

    // 用户信息缓存 - 较长过期时间
    private Cache<String, String> userInfoCache;

    // 通用缓存
    private Cache<String, Object> generalCache;

    // 待同步操作队列
    private final ConcurrentLinkedQueue<PendingOperation> pendingOperations = new ConcurrentLinkedQueue<>();

    @PostConstruct
    public void init() {
        // 初始化Token缓存 - 5分钟过期，最大1000个
        tokenCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .expireAfterAccess(2, TimeUnit.MINUTES)
                .recordStats()
                .build();

        // 初始化用户信息缓存 - 30分钟过期，最大5000个
        userInfoCache = Caffeine.newBuilder()
                .maximumSize(5000)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .recordStats()
                .build();

        // 初始化通用缓存 - 15分钟过期，最大2000个
        generalCache = Caffeine.newBuilder()
                .maximumSize(2000)
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .expireAfterAccess(5, TimeUnit.MINUTES)
                .recordStats()
                .build();

        log.info("本地缓存服务初始化完成");
    }

    /**
     * 获取缓存值
     */
    public String get(String key) {
        if (key == null) {
            return null;
        }

        // 根据key类型选择不同的缓存
        if (key.contains("userToken")) {
            return tokenCache.getIfPresent(key);
        } else if (key.contains("userInfo") || key.contains("userSummary")) {
            return userInfoCache.getIfPresent(key);
        } else {
            Object value = generalCache.getIfPresent(key);
            return value != null ? value.toString() : null;
        }
    }

    /**
     * 设置缓存值
     */
    public void put(String key, String value) {
        if (key == null || value == null) {
            return;
        }

        try {
            if (key.contains("userToken")) {
                tokenCache.put(key, value);
                log.debug("Token缓存写入: {}", key);
            } else if (key.contains("userInfo") || key.contains("userSummary")) {
                userInfoCache.put(key, value);
                log.debug("用户信息缓存写入: {}", key);
            } else {
                generalCache.put(key, value);
                log.debug("通用缓存写入: {}", key);
            }
        } catch (Exception e) {
            log.error("本地缓存写入失败, key: {}", key, e);
        }
    }

    /**
     * 删除缓存
     */
    public void remove(String key) {
        if (key == null) {
            return;
        }

        try {
            if (key.contains("userToken")) {
                tokenCache.invalidate(key);
            } else if (key.contains("userInfo") || key.contains("userSummary")) {
                userInfoCache.invalidate(key);
            } else {
                generalCache.invalidate(key);
            }
            log.debug("本地缓存删除: {}", key);
        } catch (Exception e) {
            log.error("本地缓存删除失败, key: {}", key, e);
        }
    }

    /**
     * 检查key是否存在
     */
    public boolean exists(String key) {
        if (key == null) {
            return false;
        }

        if (key.contains("userToken")) {
            return tokenCache.getIfPresent(key) != null;
        } else if (key.contains("userInfo") || key.contains("userSummary")) {
            return userInfoCache.getIfPresent(key) != null;
        } else {
            return generalCache.getIfPresent(key) != null;
        }
    }

    /**
     * 清空所有缓存
     */
    public void clearAll() {
        tokenCache.invalidateAll();
        userInfoCache.invalidateAll();
        generalCache.invalidateAll();
        log.info("所有本地缓存已清空");
    }

    /**
     * 添加待同步操作
     */
    public void addPendingOperation(String key, String operation) {
        PendingOperation pendingOp = new PendingOperation(key, operation, System.currentTimeMillis());
        pendingOperations.offer(pendingOp);

        // 限制队列大小，避免内存溢出
        while (pendingOperations.size() > 10000) {
            pendingOperations.poll();
        }

        log.debug("添加待同步操作: {} - {}", operation, key);
    }

    /**
     * 获取待同步操作
     */
    public PendingOperation getPendingOperation() {
        return pendingOperations.poll();
    }

    /**
     * 获取待同步操作数量
     */
    public int getPendingOperationCount() {
        return pendingOperations.size();
    }

    /**
     * 获取缓存统计信息
     */
    public CacheStats getCacheStats() {
        return CacheStats.builder()
                .tokenCacheSize(tokenCache.estimatedSize())
                .tokenCacheHitRate(tokenCache.stats().hitRate())
                .userInfoCacheSize(userInfoCache.estimatedSize())
                .userInfoCacheHitRate(userInfoCache.stats().hitRate())
                .generalCacheSize(generalCache.estimatedSize())
                .generalCacheHitRate(generalCache.stats().hitRate())
                .pendingOperationCount(pendingOperations.size())
                .build();
    }

    /**
     * 预热缓存 - 在Redis恢复时同步数据
     */
    public void warmUpCache(String key, String value) {
        if (key != null && value != null) {
            put(key, value);
        }
    }

    /**
     * 待同步操作实体
     * 用于存储待处理的同步操作信息，包括操作键、操作类型和时间戳
     */
    public static class PendingOperation {
        // 操作的键值，用于标识操作的对象
        private final String key;
        // 操作类型，如"CREATE"、"UPDATE"或"DELETE"
        private final String operation;
        // 操作的时间戳，用于判断操作是否过期
        private final long timestamp;

        /**
         * 构造函数，初始化待同步操作实体
         *
         * @param key       操作的键值
         * @param operation 操作类型
         * @param timestamp 操作时间戳
         */
        public PendingOperation(String key, String operation, long timestamp) {
            this.key = key;
            this.operation = operation;
            this.timestamp = timestamp;
        }

        /**
         * 获取操作的键值
         *
         * @return 操作的键值
         */
        public String getKey() {
            return key;
        }

        /**
         * 获取操作类型
         *
         * @return 操作类型
         */
        public String getOperation() {
            return operation;
        }

        /**
         * 获取操作时间戳
         *
         * @return 操作时间戳
         */
        public long getTimestamp() {
            return timestamp;
        }

        /**
         * 判断操作是否过期
         *
         * @param maxAge 最大存活时间（毫秒）
         * @return 如果操作超过最大存活时间则返回true，否则返回false
         */
        public boolean isExpired(long maxAge) {
            return System.currentTimeMillis() - timestamp > maxAge;
        }
    }

    /**
     * 缓存统计信息
     */
    public static class CacheStats {
        private final long tokenCacheSize;
        private final double tokenCacheHitRate;
        private final long userInfoCacheSize;
        private final double userInfoCacheHitRate;
        private final long generalCacheSize;
        private final double generalCacheHitRate;
        private final int pendingOperationCount;

        private CacheStats(Builder builder) {
            this.tokenCacheSize = builder.tokenCacheSize;
            this.tokenCacheHitRate = builder.tokenCacheHitRate;
            this.userInfoCacheSize = builder.userInfoCacheSize;
            this.userInfoCacheHitRate = builder.userInfoCacheHitRate;
            this.generalCacheSize = builder.generalCacheSize;
            this.generalCacheHitRate = builder.generalCacheHitRate;
            this.pendingOperationCount = builder.pendingOperationCount;
        }

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public long getTokenCacheSize() {
            return tokenCacheSize;
        }

        public double getTokenCacheHitRate() {
            return tokenCacheHitRate;
        }

        public long getUserInfoCacheSize() {
            return userInfoCacheSize;
        }

        public double getUserInfoCacheHitRate() {
            return userInfoCacheHitRate;
        }

        public long getGeneralCacheSize() {
            return generalCacheSize;
        }

        public double getGeneralCacheHitRate() {
            return generalCacheHitRate;
        }

        public int getPendingOperationCount() {
            return pendingOperationCount;
        }

        public static class Builder {
            private long tokenCacheSize;
            private double tokenCacheHitRate;
            private long userInfoCacheSize;
            private double userInfoCacheHitRate;
            private long generalCacheSize;
            private double generalCacheHitRate;
            private int pendingOperationCount;

            public Builder tokenCacheSize(long tokenCacheSize) {
                this.tokenCacheSize = tokenCacheSize;
                return this;
            }

            public Builder tokenCacheHitRate(double tokenCacheHitRate) {
                this.tokenCacheHitRate = tokenCacheHitRate;
                return this;
            }

            public Builder userInfoCacheSize(long userInfoCacheSize) {
                this.userInfoCacheSize = userInfoCacheSize;
                return this;
            }

            public Builder userInfoCacheHitRate(double userInfoCacheHitRate) {
                this.userInfoCacheHitRate = userInfoCacheHitRate;
                return this;
            }

            public Builder generalCacheSize(long generalCacheSize) {
                this.generalCacheSize = generalCacheSize;
                return this;
            }

            public Builder generalCacheHitRate(double generalCacheHitRate) {
                this.generalCacheHitRate = generalCacheHitRate;
                return this;
            }

            public Builder pendingOperationCount(int pendingOperationCount) {
                this.pendingOperationCount = pendingOperationCount;
                return this;
            }

            public CacheStats build() {
                return new CacheStats(this);
            }
        }
    }
}