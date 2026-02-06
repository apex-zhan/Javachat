package com.abin.mallchat.ai.rag.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 流式连接管理器
 * 负责跟踪活跃连接、超时检测和资源释放
 * 
 * @author zxw
 */
@Slf4j
@Service
public class StreamConnectionManager {
    
    /**
     * 活跃连接映射 (connectionId -> ConnectionInfo)
     */
    private final Map<String, ConnectionInfo> activeConnections = new ConcurrentHashMap<>();
    
    /**
     * 定时任务执行器（用于超时检测）
     */
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    /**
     * 连接超时时间（秒）
     */
    private static final int CONNECTION_TIMEOUT_SECONDS = 300;
    
    /**
     * 超时检测间隔（秒）
     */
    private static final int TIMEOUT_CHECK_INTERVAL = 60;
    
    public StreamConnectionManager() {
        // 启动超时检测任务
        scheduler.scheduleAtFixedRate(
                this::checkTimeouts,
                TIMEOUT_CHECK_INTERVAL,
                TIMEOUT_CHECK_INTERVAL,
                TimeUnit.SECONDS
        );
        log.info("流式连接管理器已启动，超时检测间隔：{}秒", TIMEOUT_CHECK_INTERVAL);
    }
    
    /**
     * 注册新连接
     * 
     * @param connectionId 连接ID
     * @param userId 用户ID
     * @param disposable 可取消的订阅
     */
    public void registerConnection(String connectionId, Long userId, Disposable disposable) {
        ConnectionInfo info = new ConnectionInfo(connectionId, userId, disposable);
        activeConnections.put(connectionId, info);
        log.info("注册流式连接，连接ID：{}，用户ID：{}，当前活跃连接数：{}", 
                connectionId, userId, activeConnections.size());
    }
    
    /**
     * 更新连接的最后活跃时间
     * 
     * @param connectionId 连接ID
     */
    public void updateLastActivity(String connectionId) {
        ConnectionInfo info = activeConnections.get(connectionId);
        if (info != null) {
            info.updateLastActivity();
            log.debug("更新连接活跃时间，连接ID：{}", connectionId);
        }
    }
    
    /**
     * 注销连接
     * 
     * @param connectionId 连接ID
     */
    public void unregisterConnection(String connectionId) {
        ConnectionInfo info = activeConnections.remove(connectionId);
        if (info != null) {
            // 取消订阅，释放资源
            if (info.getDisposable() != null && !info.getDisposable().isDisposed()) {
                info.getDisposable().dispose();
            }
            log.info("注销流式连接，连接ID：{}，用户ID：{}，连接时长：{}秒，当前活跃连接数：{}", 
                    connectionId, info.getUserId(), info.getDurationSeconds(), activeConnections.size());
        }
    }
    
    /**
     * 获取活跃连接数
     * 
     * @return 活跃连接数
     */
    public int getActiveConnectionCount() {
        return activeConnections.size();
    }
    
    /**
     * 获取指定用户的活跃连接数
     * 
     * @param userId 用户ID
     * @return 活跃连接数
     */
    public long getUserConnectionCount(Long userId) {
        return activeConnections.values().stream()
                .filter(info -> userId.equals(info.getUserId()))
                .count();
    }
    
    /**
     * 检查超时连接并清理
     */
    private void checkTimeouts() {
        try {
            LocalDateTime now = LocalDateTime.now();
            activeConnections.entrySet().removeIf(entry -> {
                ConnectionInfo info = entry.getValue();
                long idleSeconds = info.getIdleSeconds();
                
                if (idleSeconds > CONNECTION_TIMEOUT_SECONDS) {
                    log.warn("检测到超时连接，连接ID：{}，用户ID：{}，空闲时长：{}秒", 
                            entry.getKey(), info.getUserId(), idleSeconds);
                    
                    // 取消订阅
                    if (info.getDisposable() != null && !info.getDisposable().isDisposed()) {
                        info.getDisposable().dispose();
                    }
                    
                    return true;  // 移除该连接
                }
                return false;
            });
            
            if (activeConnections.size() > 0) {
                log.debug("超时检测完成，当前活跃连接数：{}", activeConnections.size());
            }
            
        } catch (Exception e) {
            log.error("超时检测发生异常", e);
        }
    }
    
    /**
     * 关闭连接管理器
     */
    public void shutdown() {
        log.info("关闭流式连接管理器，清理所有连接");
        
        // 取消所有活跃连接
        activeConnections.values().forEach(info -> {
            if (info.getDisposable() != null && !info.getDisposable().isDisposed()) {
                info.getDisposable().dispose();
            }
        });
        activeConnections.clear();
        
        // 关闭定时任务
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        log.info("流式连接管理器已关闭");
    }
    
    /**
     * 连接信息
     */
    private static class ConnectionInfo {
        private final String connectionId;
        private final Long userId;
        private final Disposable disposable;
        private final LocalDateTime createTime;
        private LocalDateTime lastActivityTime;
        
        public ConnectionInfo(String connectionId, Long userId, Disposable disposable) {
            this.connectionId = connectionId;
            this.userId = userId;
            this.disposable = disposable;
            this.createTime = LocalDateTime.now();
            this.lastActivityTime = LocalDateTime.now();
        }
        
        public void updateLastActivity() {
            this.lastActivityTime = LocalDateTime.now();
        }
        
        public Long getUserId() {
            return userId;
        }
        
        public Disposable getDisposable() {
            return disposable;
        }
        
        public long getDurationSeconds() {
            return java.time.Duration.between(createTime, LocalDateTime.now()).getSeconds();
        }
        
        public long getIdleSeconds() {
            return java.time.Duration.between(lastActivityTime, LocalDateTime.now()).getSeconds();
        }
    }
}
