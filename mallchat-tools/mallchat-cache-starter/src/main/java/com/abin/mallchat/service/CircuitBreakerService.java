package com.abin.mallchat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 熔断器服务
 * 实现Redis访问的熔断保护机制
 */
@Service
@Slf4j
public class CircuitBreakerService {

    // 熔断器状态
    public enum CircuitState {
        CLOSED,    // 关闭状态 - 正常工作
        OPEN,      // 开启状态 - 熔断中
        HALF_OPEN  // 半开状态 - 尝试恢复
    }

    // 当前熔断器状态
    private volatile CircuitState currentState = CircuitState.CLOSED;
    
    // 失败计数器
    private final AtomicInteger failureCount = new AtomicInteger(0);
    
    // 成功计数器
    private final AtomicInteger successCount = new AtomicInteger(0);
    
    // 最后一次失败时间
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    
    // 最后一次状态变更时间
    private final AtomicLong lastStateChangeTime = new AtomicLong(System.currentTimeMillis());

    // 配置参数
    private static final int FAILURE_THRESHOLD = 5;           // 失败阈值
    private static final int SUCCESS_THRESHOLD = 3;           // 恢复成功阈值
    private static final long TIMEOUT_DURATION = 60000;      // 熔断超时时间（毫秒）
    private static final long HALF_OPEN_TIMEOUT = 30000;     // 半开状态超时时间（毫秒）
    private static final long RESET_TIMEOUT = 300000;        // 重置计数器超时时间（毫秒）

    @PostConstruct
    public void init() {
        log.info("熔断器服务初始化完成 - 失败阈值: {}, 超时时间: {}ms", FAILURE_THRESHOLD, TIMEOUT_DURATION);
    }

    /**
     * 记录成功操作
     */
    public void recordSuccess() {
        successCount.incrementAndGet();
        
        switch (currentState) {
            case HALF_OPEN:
                // 半开状态下连续成功达到阈值，转为关闭状态
                if (successCount.get() >= SUCCESS_THRESHOLD) {
                    transitionTo(CircuitState.CLOSED);
                    resetCounters();
                    log.info("熔断器恢复正常，状态转为CLOSED");
                }
                break;
            case OPEN:
                // 开启状态下不应该有成功操作，但如果有，重置失败计数
                failureCount.set(0);
                break;
            case CLOSED:
                // 关闭状态下定期重置计数器
                if (System.currentTimeMillis() - lastStateChangeTime.get() > RESET_TIMEOUT) {
                    resetCounters();
                }
                break;
        }
    }

    /**
     * 记录失败操作
     */
    public void recordFailure() {
        failureCount.incrementAndGet();
        lastFailureTime.set(System.currentTimeMillis());
        
        switch (currentState) {
            case CLOSED:
                // 关闭状态下失败次数达到阈值，转为开启状态
                if (failureCount.get() >= FAILURE_THRESHOLD) {
                    transitionTo(CircuitState.OPEN);
                    log.warn("Redis连续失败{}次，熔断器开启", FAILURE_THRESHOLD);
                }
                break;
            case HALF_OPEN:
                // 半开状态下任何失败都会转回开启状态
                transitionTo(CircuitState.OPEN);
                log.warn("半开状态下操作失败，熔断器重新开启");
                break;
            case OPEN:
                // 开启状态下继续累计失败次数
                break;
        }
    }

    /**
     * 检查熔断器是否开启
     */
    public boolean isCircuitOpen() {
        switch (currentState) {
            case OPEN:
                // 检查是否可以转为半开状态
                if (System.currentTimeMillis() - lastStateChangeTime.get() > TIMEOUT_DURATION) {
                    transitionTo(CircuitState.HALF_OPEN);
                    log.info("熔断器超时，状态转为HALF_OPEN，开始尝试恢复");
                    return false; // 半开状态允许少量请求通过
                }
                return true;
            case HALF_OPEN:
                // 半开状态检查超时
                if (System.currentTimeMillis() - lastStateChangeTime.get() > HALF_OPEN_TIMEOUT) {
                    transitionTo(CircuitState.OPEN);
                    log.warn("半开状态超时，熔断器重新开启");
                    return true;
                }
                return false; // 半开状态允许请求通过
            case CLOSED:
            default:
                return false;
        }
    }

    /**
     * 获取当前熔断器状态
     */
    public CircuitState getCurrentState() {
        return currentState;
    }

    /**
     * 获取失败次数
     */
    public int getFailureCount() {
        return failureCount.get();
    }

    /**
     * 获取成功次数
     */
    public int getSuccessCount() {
        return successCount.get();
    }

    /**
     * 手动重置熔断器
     */
    public void reset() {
        transitionTo(CircuitState.CLOSED);
        resetCounters();
        log.info("熔断器手动重置");
    }

    /**
     * 强制开启熔断器
     */
    public void forceOpen() {
        transitionTo(CircuitState.OPEN);
        log.warn("熔断器被强制开启");
    }

    /**
     * 获取熔断器统计信息
     */
    public CircuitBreakerStats getStats() {
        return new CircuitBreakerStats(
                currentState,
                failureCount.get(),
                successCount.get(),
                lastFailureTime.get(),
                lastStateChangeTime.get(),
                System.currentTimeMillis() - lastStateChangeTime.get()
        );
    }

    /**
     * 状态转换
     */
    private void transitionTo(CircuitState newState) {
        CircuitState oldState = currentState;
        currentState = newState;
        lastStateChangeTime.set(System.currentTimeMillis());
        
        if (oldState != newState) {
            log.info("熔断器状态变更: {} -> {}", oldState, newState);
        }
    }

    /**
     * 重置计数器
     */
    private void resetCounters() {
        failureCount.set(0);
        successCount.set(0);
    }

    /**
     * 熔断器统计信息
     */
    public static class CircuitBreakerStats {
        private final CircuitState state;
        private final int failureCount;
        private final int successCount;
        private final long lastFailureTime;
        private final long lastStateChangeTime;
        private final long currentStateDuration;

        public CircuitBreakerStats(CircuitState state, int failureCount, int successCount,
                                 long lastFailureTime, long lastStateChangeTime, long currentStateDuration) {
            this.state = state;
            this.failureCount = failureCount;
            this.successCount = successCount;
            this.lastFailureTime = lastFailureTime;
            this.lastStateChangeTime = lastStateChangeTime;
            this.currentStateDuration = currentStateDuration;
        }

        // Getters
        public CircuitState getState() { return state; }
        public int getFailureCount() { return failureCount; }
        public int getSuccessCount() { return successCount; }
        public long getLastFailureTime() { return lastFailureTime; }
        public long getLastStateChangeTime() { return lastStateChangeTime; }
        public long getCurrentStateDuration() { return currentStateDuration; }
        
        public boolean isHealthy() {
            return state == CircuitState.CLOSED;
        }
    }
}