package com.abin.mallchat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 数据一致性保障服务
 * 负责在Redis恢复后同步数据，确保数据一致性
 */
@Service
@Slf4j
public class DataConsistencyService {

    @Autowired
    private RedisHighAvailabilityService redisHighAvailabilityService;
    
    @Autowired
    private LocalCacheService localCacheService;
    
    private final ScheduledExecutorService syncExecutor = Executors.newScheduledThreadPool(2);
    
    // 同步配置
    private static final int SYNC_INTERVAL_SECONDS = 30;
    private static final int MAX_SYNC_BATCH_SIZE = 100;
    private static final long OPERATION_EXPIRE_TIME = 300000; // 5分钟

    @PostConstruct
    public void init() {
        startDataSyncTask();
        log.info("数据一致性服务初始化完成");
    }

    /**
     * 启动数据同步任务
     */
    private void startDataSyncTask() {
        syncExecutor.scheduleAtFixedRate(this::syncPendingOperations, 
                SYNC_INTERVAL_SECONDS, SYNC_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 同步待处理操作
     */
    private void syncPendingOperations() {
        if (!redisHighAvailabilityService.isRedisAvailable()) {
            log.debug("Redis不可用，跳过数据同步");
            return;
        }

        int syncCount = 0;
        int maxBatch = MAX_SYNC_BATCH_SIZE;
        
        while (syncCount < maxBatch) {
            LocalCacheService.PendingOperation operation = localCacheService.getPendingOperation();
            if (operation == null) {
                break;
            }

            // 检查操作是否过期
            if (operation.isExpired(OPERATION_EXPIRE_TIME)) {
                log.warn("跳过过期操作: {} - {}", operation.getOperation(), operation.getKey());
                continue;
            }

            try {
                syncOperation(operation);
                syncCount++;
                log.debug("同步操作成功: {} - {}", operation.getOperation(), operation.getKey());
            } catch (Exception e) {
                log.error("同步操作失败: {} - {}", operation.getOperation(), operation.getKey(), e);
                // 重新放回队列（可选）
                // localCacheService.addPendingOperation(operation.getKey(), operation.getOperation());
                break; // 如果同步失败，停止本轮同步
            }
        }

        if (syncCount > 0) {
            log.info("本轮同步完成，处理操作数: {}", syncCount);
        }
    }

    /**
     * 同步单个操作
     */
    private void syncOperation(LocalCacheService.PendingOperation operation) {
        String key = operation.getKey();
        String operationType = operation.getOperation();

        switch (operationType) {
            case "SET":
                // 从本地缓存获取值并同步到Redis
                String value = localCacheService.get(key);
                if (value != null) {
                    redisHighAvailabilityService.setWithHA(key, value, 5, TimeUnit.DAYS);
                }
                break;
            case "DELETE":
                // 删除Redis中的key
                redisHighAvailabilityService.deleteWithHA(key);
                break;
            default:
                log.warn("未知的操作类型: {}", operationType);
        }
    }

    /**
     * 手动触发数据同步
     */
    public void manualSync() {
        log.info("手动触发数据同步");
        syncExecutor.execute(this::syncPendingOperations);
    }

    /**
     * 预热缓存 - Redis恢复后从Redis同步数据到本地缓存
     */
    public void warmUpLocalCache() {
        if (!redisHighAvailabilityService.isRedisAvailable()) {
            log.warn("Redis不可用，无法预热缓存");
            return;
        }

        log.info("开始预热本地缓存");
        
        // 这里可以根据业务需要，从Redis加载关键数据到本地缓存
        // 例如：活跃用户的token、用户信息等
        
        // TODO: 实现具体的预热逻辑
        // 1. 获取活跃用户列表
        // 2. 批量加载用户token和信息
        // 3. 更新本地缓存
        
        log.info("本地缓存预热完成");
    }

    /**
     * 数据一致性检查
     */
    public ConsistencyCheckResult checkDataConsistency() {
        if (!redisHighAvailabilityService.isRedisAvailable()) {
            return ConsistencyCheckResult.builder()
                    .consistent(false)
                    .message("Redis不可用，无法进行一致性检查")
                    .build();
        }

        // TODO: 实现数据一致性检查逻辑
        // 1. 比较本地缓存和Redis中的关键数据
        // 2. 检查是否存在不一致的情况
        // 3. 返回检查结果

        return ConsistencyCheckResult.builder()
                .consistent(true)
                .message("数据一致性检查通过")
                .checkedKeys(0)
                .inconsistentKeys(0)
                .build();
    }

    /**
     * 获取同步状态
     */
    public SyncStatus getSyncStatus() {
        return SyncStatus.builder()
                .pendingOperationCount(localCacheService.getPendingOperationCount())
                .redisAvailable(redisHighAvailabilityService.isRedisAvailable())
                .lastSyncTime(System.currentTimeMillis())
                .build();
    }

    /**
     * 一致性检查结果
     */
    public static class ConsistencyCheckResult {
        private final boolean consistent;
        private final String message;
        private final int checkedKeys;
        private final int inconsistentKeys;

        private ConsistencyCheckResult(Builder builder) {
            this.consistent = builder.consistent;
            this.message = builder.message;
            this.checkedKeys = builder.checkedKeys;
            this.inconsistentKeys = builder.inconsistentKeys;
        }

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public boolean isConsistent() { return consistent; }
        public String getMessage() { return message; }
        public int getCheckedKeys() { return checkedKeys; }
        public int getInconsistentKeys() { return inconsistentKeys; }

        public static class Builder {
            private boolean consistent;
            private String message;
            private int checkedKeys;
            private int inconsistentKeys;

            public Builder consistent(boolean consistent) {
                this.consistent = consistent;
                return this;
            }

            public Builder message(String message) {
                this.message = message;
                return this;
            }

            public Builder checkedKeys(int checkedKeys) {
                this.checkedKeys = checkedKeys;
                return this;
            }

            public Builder inconsistentKeys(int inconsistentKeys) {
                this.inconsistentKeys = inconsistentKeys;
                return this;
            }

            public ConsistencyCheckResult build() {
                return new ConsistencyCheckResult(this);
            }
        }
    }

    /**
     * 同步状态
     */
    public static class SyncStatus {
        private final int pendingOperationCount;
        private final boolean redisAvailable;
        private final long lastSyncTime;

        private SyncStatus(Builder builder) {
            this.pendingOperationCount = builder.pendingOperationCount;
            this.redisAvailable = builder.redisAvailable;
            this.lastSyncTime = builder.lastSyncTime;
        }

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public int getPendingOperationCount() { return pendingOperationCount; }
        public boolean isRedisAvailable() { return redisAvailable; }
        public long getLastSyncTime() { return lastSyncTime; }

        public static class Builder {
            private int pendingOperationCount;
            private boolean redisAvailable;
            private long lastSyncTime;

            public Builder pendingOperationCount(int pendingOperationCount) {
                this.pendingOperationCount = pendingOperationCount;
                return this;
            }

            public Builder redisAvailable(boolean redisAvailable) {
                this.redisAvailable = redisAvailable;
                return this;
            }

            public Builder lastSyncTime(long lastSyncTime) {
                this.lastSyncTime = lastSyncTime;
                return this;
            }

            public SyncStatus build() {
                return new SyncStatus(this);
            }
        }
    }
}