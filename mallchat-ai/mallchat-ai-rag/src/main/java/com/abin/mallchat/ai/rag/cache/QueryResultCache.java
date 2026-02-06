package com.abin.mallchat.ai.rag.cache;

import com.abin.mallchat.ai.vector.domain.SearchResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;


/**
 * 查询结果缓存（Redis版本）
 * 
 * 功能：
 * 1. 缓存热门查询的向量检索结果
 * 2. 减少向量数据库查询压力
 * 3. 提升响应速度
 * 
 * 缓存策略：
 * - 缓存时间：5分钟（查询结果时效性要求较高）
 * - 缓存类型：Redis（需要跨实例共享）
 * - 缓存键：基于问题文本的MD5哈希
 * - 空值缓存：否（避免缓存无效查询）
 * 
 * 适用场景：
 * - 热门问题查询
 * - 重复查询优化
 * - 高并发场景
 * 
 * @author zxw
 */
@Slf4j
@Component
public class QueryResultCache {
    
    private static final String CACHE_PREFIX = "ai:query:result:";
    private static final long CACHE_EXPIRE_MINUTES = 5;
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 获取查询结果（带缓存）
     * 
     * @param question 问题文本
     * @param documentId 文档ID（可选）
     * @param topK 返回数量
     * @return 检索结果列表，如果缓存未命中返回null
     */
    public List<SearchResult> getQueryResult(String question, Long documentId, int topK) {
        String cacheKey = buildCacheKey(question, documentId, topK);
        
        // 从缓存查询
        String cachedJson = redisTemplate.opsForValue().get(cacheKey);
        if (cachedJson != null) {
            try {
                List<SearchResult> results = objectMapper.readValue(
                        cachedJson, 
                        new TypeReference<List<SearchResult>>() {}
                );
                log.debug("从缓存获取查询结果，问题：{}, 结果数量：{}", question, results.size());
                return results;
            } catch (JsonProcessingException e) {
                log.error("反序列化缓存结果失败", e);
                // 删除损坏的缓存
                redisTemplate.delete(cacheKey);
            }
        }
        
        log.debug("缓存未命中，问题：{}", question);
        return null;
    }
    
    /**
     * 缓存查询结果
     * 
     * @param question 问题文本
     * @param documentId 文档ID（可选）
     * @param topK 返回数量
     * @param results 检索结果
     */
    public void cacheQueryResult(String question, Long documentId, int topK, List<SearchResult> results) {
        if (results == null || results.isEmpty()) {
            log.debug("查询结果为空，不缓存");
            return;
        }
        
        String cacheKey = buildCacheKey(question, documentId, topK);
        
        try {
            String json = objectMapper.writeValueAsString(results);
            redisTemplate.opsForValue().set(cacheKey, json, CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
            log.debug("查询结果已缓存，问题：{}, 结果数量：{}", question, results.size());
        } catch (JsonProcessingException e) {
            log.error("序列化查询结果失败", e);
        }
    }
    
    /**
     * 使缓存失效（基于文档ID）
     * 
     * 当文档更新或删除时，需要清除相关的查询缓存
     * 
     * @param documentId 文档ID
     */
    public void invalidateByDocumentId(Long documentId) {
        log.debug("使文档相关查询缓存失效，文档ID：{}", documentId);
        
        // 使用模糊匹配删除所有相关缓存
        String pattern = CACHE_PREFIX + "*:doc:" + documentId + ":*";
        
        try {
            redisTemplate.keys(pattern).forEach(key -> {
                redisTemplate.delete(key);
                log.debug("删除缓存键：{}", key);
            });
        } catch (Exception e) {
            log.error("删除文档相关缓存失败", e);
        }
    }
    
    /**
     * 清空所有查询缓存
     * 
     * 用于系统维护或数据更新后的缓存清理
     */
    public void clearAllQueryCache() {
        log.info("开始清空所有查询缓存");
        
        String pattern = CACHE_PREFIX + "*";
        
        try {
            redisTemplate.keys(pattern).forEach(key -> {
                redisTemplate.delete(key);
            });
            log.info("所有查询缓存已清空");
        } catch (Exception e) {
            log.error("清空查询缓存失败", e);
        }
    }
    
    /**
     * 构造缓存键
     * 
     * 格式：ai:query:result:{questionHash}:doc:{documentId}:top:{topK}
     * 
     * @param question 问题文本
     * @param documentId 文档ID
     * @param topK 返回数量
     * @return 缓存键
     */
    private String buildCacheKey(String question, Long documentId, int topK) {
        // 对问题文本进行MD5哈希，避免键过长
        String questionHash = DigestUtils.md5DigestAsHex(question.getBytes(StandardCharsets.UTF_8));
        
        StringBuilder keyBuilder = new StringBuilder(CACHE_PREFIX);
        keyBuilder.append(questionHash);
        keyBuilder.append(":doc:");
        keyBuilder.append(documentId != null ? documentId : "all");
        keyBuilder.append(":top:");
        keyBuilder.append(topK);
        
        return keyBuilder.toString();
    }
    
    /**
     * 获取缓存统计信息
     * 
     * @return 缓存键数量
     */
    public long getCacheCount() {
        String pattern = CACHE_PREFIX + "*";
        return redisTemplate.keys(pattern).size();
    }
}
