package com.abin.mallchat.ai.rag.cache;

import com.abin.mallchat.ai.common.dao.KnowledgeDocumentDao;
import com.abin.mallchat.ai.common.domain.entity.KnowledgeDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 索引状态缓存（Redis版本）
 * <p>
 * 功能：
 * 1. 缓存文档索引状态（PENDING, INDEXING, COMPLETED, FAILED）
 * 2. 支持快速查询索引状态
 * 3. 支持批量查询
 * 4. 自动过期和手动失效
 * <p>
 * 缓存策略：
 * - 缓存时间：10分钟（索引状态变化较频繁）
 * - 缓存类型：Redis（需要跨实例共享）
 * - 空值缓存：否（索引状态必须存在）
 *
 * @author Abin
 */
@Slf4j
@Component
public class IndexStatusCache {

    private static final String CACHE_PREFIX = "ai:index:status:";
    private static final long CACHE_EXPIRE_MINUTES = 10;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private KnowledgeDocumentDao knowledgeDocumentDao;

    /**
     * 获取索引状态（带缓存）
     *
     * @param documentId 文档ID
     * @return 索引状态
     */
    public String getIndexStatus(Long documentId) {
        String cacheKey = CACHE_PREFIX + documentId;

        // 先从缓存查询
        String cachedStatus = redisTemplate.opsForValue().get(cacheKey);
        if (cachedStatus != null) {
            log.debug("从缓存获取索引状态，文档ID：{}, 状态：{}", documentId, cachedStatus);
            return cachedStatus;
        }

        // 缓存未命中，从数据库查询
        log.debug("缓存未命中，从数据库查询索引状态，文档ID：{}", documentId);
        KnowledgeDocument document = knowledgeDocumentDao.getById(documentId);

        if (document == null) {
            log.warn("文档不存在，文档ID：{}", documentId);
            return null;
        }

        String status = document.getIndexStatus();

        // 写入缓存
        redisTemplate.opsForValue().set(cacheKey, status, CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        log.debug("索引状态已缓存，文档ID：{}, 状态：{}", documentId, status);

        return status;
    }

    /**
     * 批量获取索引状态（带缓存）
     *
     * @param documentIds 文档ID列表
     * @return 文档ID -> 索引状态的映射
     */
    public Map<Long, String> getIndexStatusBatch(List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()){
            return Map.of();
        }

        log.debug("批量查询索引状态，数量：{}", documentIds.size());

        // 去重
        documentIds = documentIds.stream().distinct().collect(Collectors.toList());

        // 构造缓存键
        List<String> cacheKeys = documentIds.stream()
                .map(id -> CACHE_PREFIX + id)
                .collect(Collectors.toList());

        // 批量从缓存查询
        List<String> cachedStatuses = redisTemplate.opsForValue().multiGet(cacheKeys);

        // 构造结果Map
        List<Long> finalDocumentIds = documentIds;
        Map<Long, String> resultMap = documentIds.stream()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> {
                            int index = finalDocumentIds.indexOf(id);
                            return cachedStatuses != null && cachedStatuses.get(index) != null
                                    ? cachedStatuses.get(index)
                                    : null;
                        }
                ));

        // 找出缓存未命中的ID
        List<Long> missedIds = resultMap.entrySet().stream()
                .filter(entry -> entry.getValue() == null)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (!missedIds.isEmpty()) {
            log.debug("缓存未命中，从数据库查询，数量：{}", missedIds.size());

            // 从数据库批量查询
            List<KnowledgeDocument> documentsFromDb = knowledgeDocumentDao.listByIds(missedIds);

            // 更新结果Map和缓存
            for (KnowledgeDocument document : documentsFromDb) {
                String status = document.getIndexStatus();
                resultMap.put(document.getId(), status);

                // 写入缓存
                String cacheKey = CACHE_PREFIX + document.getId();
                redisTemplate.opsForValue().set(cacheKey, status, CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
            }
        }

        log.debug("批量查询完成，缓存命中率：{}/{}",
                documentIds.size() - missedIds.size(), documentIds.size());

        return resultMap;
    }

    /**
     * 更新索引状态（同时更新缓存）
     *
     * @param documentId 文档ID
     * @param status     新状态
     */
    public void updateIndexStatus(Long documentId, String status) {
        log.debug("更新索引状态，文档ID：{}, 状态：{}", documentId, status);

        String cacheKey = CACHE_PREFIX + documentId;

        // 更新缓存
        redisTemplate.opsForValue().set(cacheKey, status, CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);

        log.debug("索引状态缓存已更新，文档ID：{}, 状态：{}", documentId, status);
    }

    /**
     * 使缓存失效（单个）
     *
     * @param documentId 文档ID
     */
    public void invalidateIndexStatus(Long documentId) {
        log.debug("使索引状态缓存失效，文档ID：{}", documentId);

        String cacheKey = CACHE_PREFIX + documentId;
        redisTemplate.delete(cacheKey);
    }

    /**
     * 使缓存失效（批量）
     *
     * @param documentIds 文档ID列表
     */
    public void invalidateIndexStatusBatch(List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return;
        }

        log.debug("批量使索引状态缓存失效，数量：{}", documentIds.size());

        List<String> cacheKeys = documentIds.stream()
                .map(id -> CACHE_PREFIX + id)
                .collect(Collectors.toList());

        redisTemplate.delete(cacheKeys);
    }

    /**
     * 检查索引是否就绪（COMPLETED状态）
     *
     * @param documentId 文档ID
     * @return true表示就绪，false表示未就绪
     */
    public boolean isIndexReady(Long documentId) {
        String status = getIndexStatus(documentId);
        return "COMPLETED".equals(status);
    }

    /**
     * 批量检查索引是否就绪
     *
     * @param documentIds 文档ID列表
     * @return 文档ID -> 是否就绪的映射
     */
    public Map<Long, Boolean> isIndexReadyBatch(List<Long> documentIds) {
        Map<Long, String> statusMap = getIndexStatusBatch(documentIds);

        return statusMap.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> "COMPLETED".equals(entry.getValue())
                ));
    }
}
