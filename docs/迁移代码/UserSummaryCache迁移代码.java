package com.abin.mallchat.common.user.service.cache;

import com.abin.mallchat.common.user.dao.UserBackpackDao;
import com.abin.mallchat.common.user.domain.dto.SummeryInfoDTO;
import com.abin.mallchat.common.user.domain.entity.*;
import com.abin.mallchat.common.user.domain.enums.ItemTypeEnum;
import com.abin.mallchat.singleflight.SingleFlight;
import com.abin.mallchat.utils.JetCacheUtils;
import com.alicp.jetcache.Cache;
import com.alicp.jetcache.anno.CacheInvalidate;
import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.anno.Cached;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户综合信息缓存（JetCache版本）
 * 
 * 特点：
 * 1. 复用 UserInfoCache 的批量查询
 * 2. 组合用户基本信息 + 徽章信息
 * 3. 缓存时间更长（10分钟）
 */
@Component
public class UserSummaryCache {
    
    @Autowired
    private UserInfoCache userInfoCache;
    
    @Autowired
    private UserBackpackDao userBackpackDao;
    
    @Autowired
    private ItemCache itemCache;
    
    @Autowired
    private SingleFlight singleFlight;
    
    /**
     * 单个查询（注解式）
     */
    @Cached(
        name = "user:summary:",
        key = "#uid",
        expire = 600,                   // Redis 10分钟
        cacheType = CacheType.BOTH,     // 两级缓存
        localExpire = 120,              // 本地 2分钟
        cacheNullValue = true
    )
    public SummeryInfoDTO getUserSummary(Long uid) {
        return loadUserSummary(Collections.singletonList(uid)).get(uid);
    }

    
    /**
     * 批量查询（编程式 + SingleFlight）
     */
    public Map<Long, SummeryInfoDTO> getUserSummaryBatch(List<Long> uidList) {
        if (uidList == null || uidList.isEmpty()) {
            return new HashMap<>();
        }
        
        uidList = uidList.stream().distinct().collect(Collectors.toList());
        
        Cache<Long, SummeryInfoDTO> cache = JetCacheUtils.create(
            "user:summary:",
            Duration.ofMinutes(10),
            true
        );
        
        Map<Long, SummeryInfoDTO> result = new HashMap<>();
        List<Long> missIds = new ArrayList<>();
        
        // 批量查询缓存
        for (Long uid : uidList) {
            SummeryInfoDTO summary = cache.get(uid);
            if (summary != null) {
                result.put(uid, summary);
            } else {
                missIds.add(uid);
            }
        }
        
        // 批量加载未命中的数据
        if (!missIds.isEmpty()) {
            String key = "summary:batch:" + missIds.hashCode();
            Map<Long, SummeryInfoDTO> loadedSummaries = singleFlight.execute(key, () -> {
                return loadUserSummary(missIds);
            });
            
            loadedSummaries.forEach(cache::put);
            result.putAll(loadedSummaries);
        }
        
        return result;
    }
    
    /**
     * 加载用户综合信息
     * 
     * 实现思路：
     * 1. 批量查询用户基本信息（复用UserInfoCache）
     * 2. 查询所有徽章配置
     * 3. 批量查询用户徽章
     * 4. 组装综合信息
     */
    private Map<Long, SummeryInfoDTO> loadUserSummary(List<Long> uidList) {
        // 1. 批量查询用户基本信息（复用UserInfoCache的批量查询）
        Map<Long, User> userMap = userInfoCache.getUserInfoBatch(uidList);
        
        // 2. 查询所有徽章配置
        List<ItemConfig> itemConfigs = itemCache.getByType(ItemTypeEnum.BADGE.getType());
        List<Long> itemIds = itemConfigs.stream()
            .map(ItemConfig::getId)
            .collect(Collectors.toList());
        
        // 3. 批量查询用户徽章
        List<UserBackpack> backpacks = userBackpackDao.getByItemIds(uidList, itemIds);
        Map<Long, List<UserBackpack>> userBadgeMap = backpacks.stream()
            .collect(Collectors.groupingBy(UserBackpack::getUid));
        
        // 4. 组装综合信息
        return uidList.stream()
            .map(uid -> {
                User user = userMap.get(uid);
                if (user == null) {
                    return null;
                }
                
                List<UserBackpack> userBackpacks = userBadgeMap.getOrDefault(uid, new ArrayList<>());
                
                SummeryInfoDTO dto = new SummeryInfoDTO();
                dto.setUid(user.getId());
                dto.setName(user.getName());
                dto.setAvatar(user.getAvatar());
                dto.setLocPlace(Optional.ofNullable(user.getIpInfo())
                    .map(IpInfo::getUpdateIpDetail)
                    .map(IpDetail::getCity)
                    .orElse(null));
                dto.setWearingItemId(user.getItemId());
                dto.setItemIds(userBackpacks.stream()
                    .map(UserBackpack::getItemId)
                    .collect(Collectors.toList()));
                
                return dto;
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(SummeryInfoDTO::getUid, s -> s));
    }
    
    /**
     * 用户信息更新时，失效综合信息缓存
     */
    @CacheInvalidate(name = "user:summary:", key = "#uid")
    public void invalidateUserSummary(Long uid) {
        // 仅失效缓存，不做其他操作
    }
    
    /**
     * 批量失效缓存
     */
    public void invalidateUserSummaryBatch(List<Long> uidList) {
        Cache<Long, SummeryInfoDTO> cache = JetCacheUtils.create(
            "user:summary:",
            Duration.ofMinutes(10),
            true
        );
        
        uidList.forEach(cache::remove);
    }
}
