package com.abin.mallchat.common.chat.service.cache;

import com.abin.mallchat.common.chat.dao.RoomFriendDao;
import com.abin.mallchat.common.chat.domain.entity.RoomFriend;
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
 * 好友房间信息缓存（JetCache版本）
 */
@Component
public class RoomFriendCache {
    
    @Autowired
    private RoomFriendDao roomFriendDao;
    
    @Autowired
    private SingleFlight singleFlight;
    
    @Cached(
        name = "friend:room:",
        key = "#roomId",
        expire = 300,
        cacheType = CacheType.BOTH,
        localExpire = 60,
        cacheNullValue = true
    )
    public RoomFriend getRoomFriend(Long roomId) {
        return roomFriendDao.getByRoomId(roomId);
    }
    
    public Map<Long, RoomFriend> getRoomFriendBatch(List<Long> roomIds) {
        if (roomIds == null || roomIds.isEmpty()) {
            return new HashMap<>();
        }
        
        roomIds = roomIds.stream().distinct().collect(Collectors.toList());
        
        Cache<Long, RoomFriend> cache = JetCacheUtils.create(
            "friend:room:",
            Duration.ofMinutes(5),
            true
        );
        
        Map<Long, RoomFriend> result = new HashMap<>();
        List<Long> missIds = new ArrayList<>();

        
        for (Long roomId : roomIds) {
            RoomFriend friend = cache.get(roomId);
            if (friend != null) {
                result.put(roomId, friend);
            } else {
                missIds.add(roomId);
            }
        }
        
        if (!missIds.isEmpty()) {
            String key = "friend:batch:" + missIds.hashCode();
            Map<Long, RoomFriend> loadedFriends = singleFlight.execute(key, () -> {
                List<RoomFriend> friends = roomFriendDao.listByRoomIds(missIds);
                return friends.stream()
                    .collect(Collectors.toMap(RoomFriend::getRoomId, f -> f));
            });
            
            loadedFriends.forEach(cache::put);
            result.putAll(loadedFriends);
        }
        
        return result;
    }
    
    @CacheInvalidate(name = "friend:room:", key = "#friend.roomId")
    public void updateRoomFriend(RoomFriend friend) {
        roomFriendDao.updateById(friend);
    }
    
    @CacheInvalidate(name = "friend:room:", key = "#roomId")
    public void deleteRoomFriend(Long roomId) {
        roomFriendDao.removeByRoomId(roomId);
    }
}
