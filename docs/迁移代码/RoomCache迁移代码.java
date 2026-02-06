package com.abin.mallchat.common.chat.service.cache;

import com.abin.mallchat.common.chat.dao.RoomDao;
import com.abin.mallchat.common.chat.domain.entity.Room;
import com.abin.mallchat.singleflight.SingleFlight;
import com.abin.mallchat.utils.JetCacheUtils;
import com.alicp.jetcache.Cache;
import com.alicp.jetcache.anno.CacheInvalidate;
import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.anno.CacheUpdate;
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
 * 房间信息缓存（JetCache版本）
 * 
 * 迁移说明：
 * 1. 移除继承 AbstractRedisStringCache
 * 2. 使用 @Cached 注解实现单个查询
 * 3. 使用编程式API + SingleFlight 实现批量查询
 * 4. 使用 @CacheInvalidate 实现缓存失效
 */
@Component
public class RoomCache {
    
    @Autowired
    private RoomDao roomDao;
    
    @Autowired
    private SingleFlight singleFlight;
    
    /**
     * 单个查询（注解式）
     * 
     * 配置说明：
     * - name: 缓存区域名称
     * - key: 缓存key（SpEL表达式）
     * - expire: Redis过期时间（秒）
     * - cacheType: BOTH表示两级缓存（Caffeine + Redis）
     * - localExpire: 本地缓存过期时间（秒）
     * - cacheNullValue: 缓存null值，防止缓存穿透
     */
    @Cached(
        name = "room:info:",
        key = "#roomId",
        expire = 300,                   // Redis 5分钟
        cacheType = CacheType.BOTH,     // 两级缓存
        localExpire = 60,               // 本地 1分钟
        cacheNullValue = true           // 防止缓存穿透
    )
    public Room getRoom(Long roomId) {
        return roomDao.getById(roomId);
    }

    
    /**
     * 批量查询（编程式 + SingleFlight）
     * 
     * 实现思路：
     * 1. 创建缓存实例
     * 2. 遍历查询缓存
     * 3. 收集未命中的ID
     * 4. 使用SingleFlight批量加载（防止缓存击穿）
     * 5. 批量写入缓存
     * 6. 合并结果返回
     */
    public Map<Long, Room> getRoomBatch(List<Long> roomIds) {
        if (roomIds == null || roomIds.isEmpty()) {
            return new HashMap<>();
        }
        
        // 去重
        roomIds = roomIds.stream().distinct().collect(Collectors.toList());
        
        // 创建缓存实例
        Cache<Long, Room> cache = JetCacheUtils.create(
            "room:info:",
            Duration.ofMinutes(5),      // 5分钟过期
            true                        // 缓存null值
        );
        
        Map<Long, Room> result = new HashMap<>();
        List<Long> missIds = new ArrayList<>();
        
        // 1. 批量查询缓存
        for (Long roomId : roomIds) {
            Room room = cache.get(roomId);
            if (room != null) {
                result.put(roomId, room);
            } else {
                missIds.add(roomId);
            }
        }
        
        // 2. 批量加载未命中的数据（使用SingleFlight防击穿）
        if (!missIds.isEmpty()) {
            String key = "room:batch:" + missIds.hashCode();
            Map<Long, Room> loadedRooms = singleFlight.execute(key, () -> {
                List<Room> rooms = roomDao.listByIds(missIds);
                return rooms.stream()
                    .collect(Collectors.toMap(Room::getId, r -> r));
            });
            
            // 3. 批量写入缓存
            loadedRooms.forEach(cache::put);
            result.putAll(loadedRooms);
        }
        
        return result;
    }
    
    /**
     * 更新房间信息并失效缓存
     * 
     * @CacheInvalidate 会自动：
     * 1. 删除本地缓存（Caffeine）
     * 2. 删除远程缓存（Redis）
     * 3. 下次查询时重新加载最新数据
     */
    @CacheInvalidate(name = "room:info:", key = "#room.id")
    public void updateRoom(Room room) {
        roomDao.updateById(room);
    }
    
    /**
     * 更新房间信息并同步更新缓存
     * 
     * @CacheUpdate 会自动：
     * 1. 更新数据库
     * 2. 直接更新缓存值（不需要删除再加载）
     * 3. 避免缓存穿透
     */
    @CacheUpdate(
        name = "room:info:",
        key = "#room.id",
        value = "#room"
    )
    public void updateRoomWithCache(Room room) {
        roomDao.updateById(room);
    }
    
    /**
     * 删除房间并失效缓存
     */
    @CacheInvalidate(name = "room:info:", key = "#roomId")
    public void deleteRoom(Long roomId) {
        roomDao.removeById(roomId);
    }
}
