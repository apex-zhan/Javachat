package com.abin.mallchat.ai.rag.cache;

import com.abin.mallchat.ai.common.dao.KnowledgeDocumentDao;
import com.abin.mallchat.ai.common.domain.entity.KnowledgeDocument;
import com.abin.mallchat.utils.JetCacheUtils;
import com.alicp.jetcache.Cache;
import com.alicp.jetcache.anno.CacheInvalidate;
import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.anno.Cached;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文档元数据缓存（JetCache版本）
 * 
 * 功能：
 * 1. 缓存文档基本信息（标题、类型、大小等）
 * 2. 支持批量查询和缓存
 * 3. 自动失效和手动失效
 * 4. 两级缓存（本地+Redis）
 * 
 * 缓存策略：
 * - 缓存时间：30分钟
 * - 缓存类型：BOTH（本地+Redis）
 * - 空值缓存：是（防止缓存穿透）
 * 
 * @author Abin
 */
@Slf4j
@Component
public class DocumentMetadataCache {
    
    @Autowired
    private KnowledgeDocumentDao knowledgeDocumentDao;
    
    /**
     * 获取单个文档元数据（带缓存）
     * 
     * @param documentId 文档ID
     * @return 文档元数据
     */
    @Cached(name = "ai:document:metadata:", key = "#documentId", 
            expire = 1800, cacheType = CacheType.BOTH, cacheNullValue = true)
    public KnowledgeDocument getDocumentMetadata(Long documentId) {
        log.debug("从数据库查询文档元数据，文档ID：{}", documentId);
        return knowledgeDocumentDao.getById(documentId);
    }
    
    /**
     * 批量获取文档元数据（带缓存）
     * 
     * 使用JetCache的批量查询能力，自动处理缓存命中和未命中的情况
     * 
     * @param documentIds 文档ID列表
     * @return 文档元数据Map
     */
    public Map<Long, KnowledgeDocument> getDocumentMetadataBatch(List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return Map.of();
        }
        
        log.debug("批量查询文档元数据，数量：{}", documentIds.size());
        
        // 去重
        documentIds = documentIds.stream().distinct().collect(Collectors.toList());
        
        // 创建缓存实例
        Cache<Long, KnowledgeDocument> cache = JetCacheUtils.create(
                "ai:document:metadata:",
                CacheType.BOTH,
                Duration.ofMinutes(30),
                true
        );
        
        // 批量查询（自动处理缓存命中和未命中）
        Map<Long, KnowledgeDocument> cachedResults = cache.getAll(
                documentIds.stream().collect(Collectors.toSet())
        );
        
        // 找出缓存未命中的ID
        List<Long> missedIds = documentIds.stream()
                .filter(id -> !cachedResults.containsKey(id))
                .collect(Collectors.toList());
        
        if (!missedIds.isEmpty()) {
            log.debug("缓存未命中，从数据库查询，数量：{}", missedIds.size());
            
            // 从数据库批量查询
            List<KnowledgeDocument> documentsFromDb = knowledgeDocumentDao.listByIds(missedIds);
            
            // 更新缓存
            Map<Long, KnowledgeDocument> dbResults = documentsFromDb.stream()
                    .collect(Collectors.toMap(KnowledgeDocument::getId, doc -> doc));
            
            if (!dbResults.isEmpty()) {
                cache.putAll(dbResults);
                cachedResults.putAll(dbResults);
            }
            
            // 对于数据库中也不存在的ID，缓存null值（防止缓存穿透）
            missedIds.stream()
                    .filter(id -> !dbResults.containsKey(id))
                    .forEach(id -> {
                        cache.put(id, null);
                        cachedResults.put(id, null);
                    });
        }
        
        log.debug("批量查询完成，缓存命中率：{}/{}", 
                documentIds.size() - missedIds.size(), documentIds.size());
        
        return cachedResults;
    }
    
    /**
     * 使缓存失效（单个）
     * 
     * @param documentId 文档ID
     */
    @CacheInvalidate(name = "ai:document:metadata:", key = "#documentId")
    public void invalidateDocumentMetadata(Long documentId) {
        log.debug("使文档元数据缓存失效，文档ID：{}", documentId);
    }
    
    /**
     * 使缓存失效（批量）
     * 
     * @param documentIds 文档ID列表
     */
    public void invalidateDocumentMetadataBatch(List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return;
        }
        
        log.debug("批量使文档元数据缓存失效，数量：{}", documentIds.size());
        
        Cache<Long, KnowledgeDocument> cache = JetCacheUtils.create(
                "ai:document:metadata:",
                CacheType.BOTH,
                Duration.ofMinutes(30),
                true
        );
        
        // 批量删除缓存
        cache.removeAll(documentIds.stream().collect(Collectors.toSet()));
    }
    
    /**
     * 预热缓存（批量）
     * 
     * 用于系统启动时或定时任务预加载热门文档
     * 
     * @param documentIds 文档ID列表
     */
    public void warmUpCache(List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return;
        }
        
        log.info("开始预热文档元数据缓存，数量：{}", documentIds.size());
        
        // 从数据库批量查询
        List<KnowledgeDocument> documents = knowledgeDocumentDao.listByIds(documentIds);
        
        if (documents.isEmpty()) {
            log.warn("预热缓存失败，未找到任何文档");
            return;
        }
        
        // 批量写入缓存
        Cache<Long, KnowledgeDocument> cache = JetCacheUtils.create(
                "ai:document:metadata:",
                CacheType.BOTH,
                Duration.ofMinutes(30),
                true
        );
        
        Map<Long, KnowledgeDocument> cacheData = documents.stream()
                .collect(Collectors.toMap(KnowledgeDocument::getId, doc -> doc));
        
        cache.putAll(cacheData);
        
        log.info("文档元数据缓存预热完成，成功加载：{} 个文档", documents.size());
    }
}
