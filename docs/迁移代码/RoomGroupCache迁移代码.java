package com.abin.mallchat.common.chat.service.cache;

import com.abin.mallchat.common.chat.dao.RoomGroupDao;
import com.abin.mallchat.common.chat.domain.entity.RoomGroup;
import com.abin.mallchat.singleflight.SingleFlight;
import com.abin.mallchat.utils.JetCacheUtils;
import com.alicp.jetcache.Cache;
import com.alicp.jetcache.anno.CacheInvalidate;
import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.anno.Cached;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 群组信息缓存（JetCache版本）
 */
@Component
public class RoomGroupCache {
    
    @Autowired
    private RoomGroupDao roomGroupDao;
    
    @Autowired
    private SingleFlight singleFlight;
    
    /**
     * 单个查询（注解式）
     */
    @Cached(
        name = "group:info:",
        key = "#roomId",
        expire = 300,                   // Redis 5分钟
        cacheType = CacheType.BOTH,     // 两级缓存
        localExpire = 60,               // 本地 1分钟
        cacheNullValue = true
    )
    public RoomGroup getRoomGroup(Long roomId) {
        return roomGroupDao.getByRoomId(roomId);
    }
    
    /**
     * 批量查询（编程式 + SingleFlight）
     */
    public Map<Long, RoomGroup> getRoomGroupBatch(List<Long> roomIds) {
        if (roomIds == null || roomIds.isEmpty()) {
            return new HashMap<>();
        }
        
        roomIds = roomIds.stream().distinct().collect(Collectors.toList());
        
        Cache<Long, RoomGroup> cache = JetCacheUtils.create(
            "group:info:",
            Duration.ofMinutes(5),
            true
        );

        
        Map<Long, RoomGroup> result = new HashMap<>();
        List<Long> missIds = new ArrayList<>();
        
        // 批量查询缓存
        for (Long roomId : roomIds) {
            RoomGroup group = cache.get(roomId);
            if (group != null) {
                result.put(roomId, group);
            } else {
                missIds.add(roomId);
            }
        }
        
        // 批量加载未命中的数据
        if (!missIds.isEmpty()) {
            String key = "group:batch:" + missIds.hashCode();
            Map<Long, RoomGroup> loadedGroups = singleFlight.execute(key, () -> {
                List<RoomGroup> groups = roomGroupDao.listByRoomIds(missIds);
                return groups.stream()
                    .collect(Collectors.toMap(RoomGroup::getRoomId, g -> g));
            });
            
            loadedGroups.forEach(cache::put);
            result.putAll(loadedGroups);
        }
        
        return result;
    }
    
    /**
     * 更新群组信息并失效缓存
     */
    @CacheInvalidate(name = "group:info:", key = "#group.roomId")
    public void updateRoomGroup(RoomGroup group) {
        roomGroupDao.updateById(group);
    }
    
    /**
     * 删除群组并失效缓存
     */
    @CacheInvalidate(name = "group:info:", key = "#roomId")
    public void deleteRoomGroup(Long roomId) {
        roomGroupDao.removeByRoomId(roomId);
    }
}
