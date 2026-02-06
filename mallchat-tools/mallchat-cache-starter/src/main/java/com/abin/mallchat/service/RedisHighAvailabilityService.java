package com.abin.mallchat.service;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

/**
 * Redis高可用服务
 * 负责处理Redis故障切换、连接管理和健康检查
 */
@Service
@Slf4j
public class RedisHighAvailabilityService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    
    @Autowired
    private RedissonClient redissonClient;
    
    @Autowired
    private LocalCacheService localCacheService;
    
    @Autowired
    private CircuitBreakerService circuitBreakerService;
    
    // Redis健康检查间隔（秒）
    private static final int HEALTH_CHECK_INTERVAL = 30;
    
    // Redis连接超时时间（毫秒）
    private static final int CONNECTION_TIMEOUT = 3000;
    
    // 最大重试次数
    private static final int MAX_RETRY_TIMES = 3;

    @PostConstruct
    public void init() {
        // 启动Redis健康检查
        startHealthCheck();
        log.info("Redis高可用服务初始化完成");
    }

    /**
     * 高可用的Redis GET操作
     */
    public String getWithHA(String key) {
        return executeWithHA(() -> stringRedisTemplate.opsForValue().get(key), key, "GET");
    }

    /**
     * 高可用的Redis SET操作
     */
    public void setWithHA(String key, String value, long timeout, TimeUnit unit) {
        executeWithHA(() -> {
            stringRedisTemplate.opsForValue().set(key, value, timeout, unit);
            return null;
        }, key, "SET");
    }

    /**
     * 高可用的Redis DELETE操作
     */
    public Boolean deleteWithHA(String key) {
        return executeWithHA(() -> stringRedisTemplate.delete(key), key, "DELETE");
    }

    /**
     * 高可用的Redis EXISTS操作
     */
    public Boolean existsWithHA(String key) {
        return executeWithHA(() -> stringRedisTemplate.hasKey(key), key, "EXISTS");
    }

    /**
     * 执行Redis操作的高可用包装方法
     */
    private <T> T executeWithHA(RedisOperation<T> operation, String key, String operationType) {
        // 检查熔断器状态
        if (circuitBreakerService.isCircuitOpen()) {
            log.warn("Redis熔断器开启，操作类型: {}, key: {}", operationType, key);
            return handleCircuitOpen(key, operationType);
        }

        int retryCount = 0;
        Exception lastException = null;

        while (retryCount < MAX_RETRY_TIMES) {
            try {
                T result = operation.execute();
                // 操作成功，记录成功次数
                circuitBreakerService.recordSuccess();
                return result;
            } catch (Exception e) {
                lastException = e;
                retryCount++;
                log.warn("Redis操作失败，操作类型: {}, key: {}, 重试次数: {}/{}, 错误: {}", 
                        operationType, key, retryCount, MAX_RETRY_TIMES, e.getMessage());
                
                // 记录失败次数
                circuitBreakerService.recordFailure();
                
                if (retryCount < MAX_RETRY_TIMES) {
                    try {
                        Thread.sleep(100 * retryCount); // 指数退避
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        // 所有重试都失败，启用降级策略
        log.error("Redis操作最终失败，启用降级策略，操作类型: {}, key: {}", operationType, key, lastException);
        return handleRedisFailure(key, operationType);
    }

    /**
     * 处理熔断器开启的情况
     */
    @SuppressWarnings("unchecked")
    private <T> T handleCircuitOpen(String key, String operationType) {
        switch (operationType) {
            case "GET":
                // 从本地缓存获取
                return (T) localCacheService.get(key);
            case "EXISTS":
                return (T) Boolean.valueOf(localCacheService.exists(key));
            case "SET":
            case "DELETE":
                // 写操作在熔断状态下直接返回成功，但记录到待同步队列
                localCacheService.addPendingOperation(key, operationType);
                return null;
            default:
                return null;
        }
    }

    /**
     * 处理Redis故障的降级策略
     */
    @SuppressWarnings("unchecked")
    private <T> T handleRedisFailure(String key, String operationType) {
        switch (operationType) {
            case "GET":
                // 尝试从本地缓存获取
                T cachedValue = (T) localCacheService.get(key);
                if (cachedValue != null) {
                    log.info("从本地缓存获取到数据，key: {}", key);
                    return cachedValue;
                }
                // 如果是token相关的key，尝试从数据库获取
                if (key.contains("userToken")) {
                    return (T) getTokenFromDatabase(key);
                }
                return null;
            case "EXISTS":
                return (T) Boolean.valueOf(localCacheService.exists(key));
            case "SET":
                // 写入本地缓存
                localCacheService.put(key, "temp_value");
                localCacheService.addPendingOperation(key, operationType);
                return null;
            case "DELETE":
                localCacheService.remove(key);
                localCacheService.addPendingOperation(key, operationType);
                return (T) Boolean.TRUE;
            default:
                return null;
        }
    }

    /**
     * 从数据库获取token（降级方案）
     */
    private String getTokenFromDatabase(String key) {
        try {
            // 解析uid从key中
            String uidStr = key.replaceAll(".*uid_(\\d+)", "$1");
            if (StrUtil.isNotBlank(uidStr)) {
                Long uid = Long.parseLong(uidStr);
                // 这里可以从数据库或其他持久化存储获取token
                // 暂时返回null，表示需要重新登录
                log.warn("Redis故障，无法获取token，用户需要重新登录，uid: {}", uid);
            }
        } catch (Exception e) {
            log.error("从数据库获取token失败", e);
        }
        return null;
    }

    /**
     * 启动Redis健康检查
     */
    private void startHealthCheck() {
        Thread healthCheckThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    checkRedisHealth();
                    Thread.sleep(HEALTH_CHECK_INTERVAL * 1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Redis健康检查异常", e);
                }
            }
        });
        healthCheckThread.setDaemon(true);
        healthCheckThread.setName("redis-health-check");
        healthCheckThread.start();
    }

    /**
     * Redis健康检查
     */
    private void checkRedisHealth() {
        try {
            // 执行简单的ping命令
            String result = stringRedisTemplate.execute((org.springframework.data.redis.core.RedisCallback<String>) connection -> {
                return connection.ping();
            });
            
            if ("PONG".equals(result)) {
                circuitBreakerService.recordSuccess();
                log.debug("Redis健康检查通过");
            } else {
                circuitBreakerService.recordFailure();
                log.warn("Redis健康检查失败，响应: {}", result);
            }
        } catch (Exception e) {
            circuitBreakerService.recordFailure();
            log.error("Redis健康检查异常", e);
        }
    }

    /**
     * 获取Redis连接状态
     */
    public boolean isRedisAvailable() {
        try {
            String result = stringRedisTemplate.execute((org.springframework.data.redis.core.RedisCallback<String>) connection -> connection.ping());
            return "PONG".equals(result);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Redis操作接口
     */
    @FunctionalInterface
    private interface RedisOperation<T> {
        T execute() throws Exception;
    }
}