package com.abin.mallchat.common.user.service.cache;

import com.abin.mallchat.common.user.dao.UserDao;
import com.abin.mallchat.common.user.domain.entity.User;
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
 * 用户基本信息缓存（JetCache完整版）
 * 
 * 功能：
 * 1. 单个查询（注解式）
 * 2. 批量查询（编程式 + SingleFlight）
 * 3. 缓存失效（@CacheInvalidate）
 * 4. 缓存更新（@CacheUpdate）
 */
@Component
public class UserInfoCache {
    
    @Autowired
    private UserDao userDao;
    
    @Autowired
    private SingleFlight singleFlight;
    
    /**
     * 单个查询（注解式）
     * 
     * 优势：
     * - 代码简洁，仅需注解
     * - 自动管理两级缓存
     * - 自动防止缓存穿透
     */
    @Cached(
        name = "user:info:",
        key = "#userId",
        expire = 1800,                  // Redis 30分钟
        cacheType = CacheType.BOTH,     // 两级缓存
        localExpire = 60,               // 本地 1分钟
        cacheNullValue = true           // 缓存null值，防止缓存穿透
    )
    public User getUserInfo(Long userId) {
        return userDao.getById(userId);
    }

    
    /**
     * 批量查询（编程式 + SingleFlight）
     * 
     * 为什么不用注解？
     * - @Cached 不支持批量查询
     * - 需要手动实现批量逻辑
     * 
     * 优化点：
     * 1. 使用 SingleFlight 防止缓存击穿
     * 2. 批量查询数据库（1次SQL）
     * 3. 批量写入缓存（1次网络IO）
     */
    public Map<Long, User> getUserInfoBatch(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new HashMap<>();
        }
        
        // 去重
        userIds = userIds.stream().distinct().collect(Collectors.toList());
        
        // 创建缓存实例
        Cache<Long, User> cache = JetCacheUtils.create(
            "user:info:",
            Duration.ofMinutes(30),     // 30分钟过期
            true                        // 缓存null值
        );
        
        Map<Long, User> result = new HashMap<>();
        List<Long> missIds = new ArrayList<>();
        
        // 1. 批量查询缓存
        for (Long userId : userIds) {
            User user = cache.get(userId);
            if (user != null) {
                result.put(userId, user);
            } else {
                missIds.add(userId);
            }
        }
        
        // 2. 批量加载未命中的数据（使用SingleFlight防击穿）
        if (!missIds.isEmpty()) {
            // 使用 hashCode 作为 SingleFlight 的 key
            // 相同的 missIds 列表会使用同一个 key，避免重复查询
            String key = "user:batch:" + missIds.hashCode();
            
            Map<Long, User> loadedUsers = singleFlight.execute(key, () -> {
                // 批量查询数据库（1次SQL）
                List<User> users = userDao.listByIds(missIds);
                return users.stream()
                    .collect(Collectors.toMap(User::getId, u -> u));
            });
            
            // 3. 批量写入缓存
            loadedUsers.forEach(cache::put);
            result.putAll(loadedUsers);
        }
        
        return result;
    }
    
    /**
     * 更新用户信息并失效缓存
     * 
     * @CacheInvalidate 执行流程：
     * 1. 更新数据库
     * 2. 自动删除本地缓存（Caffeine）
     * 3. 自动删除远程缓存（Redis）
     * 4. 下次查询时重新加载最新数据
     * 
     * 支持在事务提交后才失效缓存
     */
    @CacheInvalidate(name = "user:info:", key = "#user.id")
    public void updateUserInfo(User user) {
        userDao.updateById(user);
    }
    
    /**
     * 更新用户信息并同步更新缓存
     * 
     * @CacheUpdate 执行流程：
     * 1. 更新数据库
     * 2. 直接更新缓存值（不需要删除再加载）
     * 3. 避免缓存穿透
     * 
     * 适用场景：
     * - 更新操作频繁
     * - 希望立即生效
     * - 避免短暂的缓存未命中
     */
    @CacheUpdate(
        name = "user:info:",
        key = "#user.id",
        value = "#user"
    )
    public void updateUserWithCache(User user) {
        userDao.updateById(user);
    }
    
    /**
     * 删除用户并失效缓存
     */
    @CacheInvalidate(name = "user:info:", key = "#userId")
    public void deleteUser(Long userId) {
        userDao.removeById(userId);
    }
    
    /**
     * 批量失效缓存
     * 
     * 使用场景：
     * - 批量更新用户信息后
     * - 需要强制刷新缓存
     */
    public void invalidateUserBatch(List<Long> userIds) {
        Cache<Long, User> cache = JetCacheUtils.create(
            "user:info:",
            Duration.ofMinutes(30),
            true
        );
        
        userIds.forEach(cache::remove);
    }
}
