package com.abin.mallchat.ai.vector.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Milvus 连接池管理器
 * 
 * 功能：
 * 1. 管理 Milvus 客户端连接池
 * 2. 复用连接，减少连接开销
 * 3. 支持连接健康检查
 * 4. 自动重连机制
 * 
 * 性能优化：
 * - 连接池大小：根据并发量调整
 * - 连接超时：避免长时间等待
 * - 健康检查：定期检查连接状态
 * 
 * @author zxw
 */
@Slf4j
@Component
public class MilvusConnectionPool {
    
    @Value("${milvus.host}")
    private String host;
    
    @Value("${milvus.port}")
    private Integer port;
    
    @Value("${milvus.connect-timeout:10}")
    private Long connectTimeout;
    
    @Value("${milvus.keep-alive-time:55}")
    private Long keepAliveTime;
    
    @Value("${milvus.keep-alive-timeout:20}")
    private Long keepAliveTimeout;
    
    @Value("${milvus.secure:false}")
    private Boolean secure;
    
    @Value("${milvus.username:}")
    private String username;
    
    @Value("${milvus.password:}")
    private String password;
    
    @Value("${milvus.database:default}")
    private String database;
    
    /**
     * 连接池大小配置
     */
    @Value("${milvus.pool.min-size:2}")
    private int minPoolSize;
    
    @Value("${milvus.pool.max-size:10}")
    private int maxPoolSize;
    
    @Value("${milvus.pool.acquire-timeout:30}")
    private long acquireTimeout;
    
    private BlockingQueue<MilvusServiceClient> connectionPool;
    private volatile boolean initialized = false;
    
    /**
     * 初始化连接池
     */
    @PostConstruct
    public void init() {
        log.info("Initializing Milvus connection pool: min={}, max={}", minPoolSize, maxPoolSize);
        
        try {
            connectionPool = new ArrayBlockingQueue<>(maxPoolSize);
            
            // 创建最小数量的连接
            for (int i = 0; i < minPoolSize; i++) {
                MilvusServiceClient client = createConnection();
                connectionPool.offer(client);
            }
            
            initialized = true;
            log.info("Milvus connection pool initialized successfully with {} connections", minPoolSize);
            
        } catch (Exception e) {
            log.error("Failed to initialize Milvus connection pool", e);
            throw new RuntimeException("Failed to initialize Milvus connection pool", e);
        }
    }
    
    /**
     * 创建新的 Milvus 连接
     */
    private MilvusServiceClient createConnection() {
        log.debug("Creating new Milvus connection to {}:{}", host, port);
        
        ConnectParam.Builder connectBuilder = ConnectParam.newBuilder()
                .withHost(host)
                .withPort(port)
                .withConnectTimeout(connectTimeout, TimeUnit.SECONDS)
                .withKeepAliveTime(keepAliveTime, TimeUnit.SECONDS)
                .withKeepAliveTimeout(keepAliveTimeout, TimeUnit.SECONDS)
                .withSecure(secure)
                .withDatabaseName(database);
        
        // 如果配置了用户名和密码，添加认证
        if (username != null && !username.isEmpty()) {
            connectBuilder.withAuthorization(username, password);
        }
        
        MilvusServiceClient client = new MilvusServiceClient(connectBuilder.build());
        log.debug("Milvus connection created successfully");
        
        return client;
    }
    
    /**
     * 从连接池获取连接
     * 
     * @return Milvus 客户端连接
     * @throws InterruptedException 如果等待超时
     */
    public MilvusServiceClient acquireConnection() throws InterruptedException {
        if (!initialized) {
            throw new IllegalStateException("Connection pool not initialized");
        }
        
        // 尝试从池中获取连接
        MilvusServiceClient client = connectionPool.poll(acquireTimeout, TimeUnit.SECONDS);
        
        if (client == null) {
            // 如果池中没有可用连接，且未达到最大连接数，创建新连接
            if (connectionPool.size() < maxPoolSize) {
                log.debug("Creating new connection as pool is empty");
                client = createConnection();
            } else {
                throw new IllegalStateException("Connection pool exhausted, timeout after " + acquireTimeout + " seconds");
            }
        }
        
        // 检查连接健康状态
        if (!isConnectionHealthy(client)) {
            log.warn("Connection is unhealthy, creating new connection");
            closeConnection(client);
            client = createConnection();
        }
        
        log.debug("Connection acquired from pool, remaining: {}", connectionPool.size());
        return client;
    }
    
    /**
     * 归还连接到连接池
     * 
     * @param client Milvus 客户端连接
     */
    public void releaseConnection(MilvusServiceClient client) {
        if (client == null) {
            return;
        }
        
        // 检查连接健康状态
        if (!isConnectionHealthy(client)) {
            log.warn("Unhealthy connection returned, closing it");
            closeConnection(client);
            return;
        }
        
        // 归还到连接池
        boolean offered = connectionPool.offer(client);
        if (!offered) {
            // 如果池已满，关闭连接
            log.debug("Connection pool is full, closing connection");
            closeConnection(client);
        } else {
            log.debug("Connection released to pool, total: {}", connectionPool.size());
        }
    }
    
    /**
     * 检查连接健康状态
     * 
     * @param client Milvus 客户端连接
     * @return true 表示健康，false 表示不健康
     */
    private boolean isConnectionHealthy(MilvusServiceClient client) {
        if (client == null) {
            return false;
        }
        
        try {
            // 简单的健康检查：尝试列出数据库
            // 如果连接正常，这个操作应该很快完成
            client.listDatabases();
            return true;
        } catch (Exception e) {
            log.warn("Connection health check failed", e);
            return false;
        }
    }
    
    /**
     * 关闭连接
     * 
     * @param client Milvus 客户端连接
     */
    private void closeConnection(MilvusServiceClient client) {
        if (client != null) {
            try {
                client.close();
                log.debug("Connection closed");
            } catch (Exception e) {
                log.error("Failed to close connection", e);
            }
        }
    }
    
    /**
     * 获取连接池统计信息
     */
    public PoolStats getPoolStats() {
        return new PoolStats(
                connectionPool.size(),
                maxPoolSize,
                maxPoolSize - connectionPool.size()
        );
    }
    
    /**
     * 连接池统计信息
     */
    public static class PoolStats {
        public final int availableConnections;
        public final int maxConnections;
        public final int activeConnections;
        
        public PoolStats(int availableConnections, int maxConnections, int activeConnections) {
            this.availableConnections = availableConnections;
            this.maxConnections = maxConnections;
            this.activeConnections = activeConnections;
        }
        
        @Override
        public String toString() {
            return String.format("PoolStats{available=%d, max=%d, active=%d}", 
                    availableConnections, maxConnections, activeConnections);
        }
    }
    
    /**
     * 销毁连接池
     */
    @PreDestroy
    public void destroy() {
        log.info("Destroying Milvus connection pool");
        
        initialized = false;
        
        // 关闭所有连接
        MilvusServiceClient client;
        while ((client = connectionPool.poll()) != null) {
            closeConnection(client);
        }
        
        log.info("Milvus connection pool destroyed");
    }
}
