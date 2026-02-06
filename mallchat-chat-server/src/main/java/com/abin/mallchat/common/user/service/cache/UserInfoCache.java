package com.abin.mallchat.common.user.service.cache;

import com.abin.mallchat.common.common.constant.RedisKey;
import com.abin.mallchat.common.common.service.cache.AbstractRedisStringCache;
import com.abin.mallchat.common.user.dao.UserDao;
import com.abin.mallchat.common.user.domain.entity.User;
import com.alicp.jetcache.anno.CacheInvalidate;
import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.anno.CacheUpdate;
import com.alicp.jetcache.anno.Cached;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Description: 用户基本信息的缓存
 * Author: <a href="https://github.com/zongzibinbin">abin</a>
 * Date: 2023-06-10
 */
@Component
public class UserInfoCache extends AbstractRedisStringCache<Long, User> {
    @Autowired
    private UserDao userDao;

    @Override
    protected String getKey(Long uid) {
        return RedisKey.getKey(RedisKey.USER_INFO_STRING, uid);
    }

    @Override
    protected Long getExpireSeconds() {
        return 5 * 60L;
    }

    /**
     * 批量加载用户信息
     *
     * @param uidList
     * @return
     */
    @Override
    protected Map<Long, User> load(List<Long> uidList) {
        List<User> needLoadUserList = userDao.listByIds(uidList);
        return needLoadUserList.stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    @Cached(
            name = "user:info:",
            key = "#userId",
            expire = 1800, //redis 30分钟
            cacheType = CacheType.BOTH,
            localExpire = 60, //本地缓存1分钟
            cacheNullValue = true   // 缓存null值，防止缓存穿透
//            penetrationProtect = true   // 防止缓存击穿（分布式锁）
    )
    public void getUserInfo(Long userId) {
        userDao.getById(userId);
    }

    /**
     * 1. 更新数据库
     * ↓
     * 2. @CacheInvalidate自动失效缓存
     * ├─ 删除本地缓存（Caffeine）
     * └─ 删除远程缓存（Redis）
     * ↓
     * 3. 下次查询时重新加载最新数据
     * <p>
     * 支持在事务提交后才失效缓存
     *
     * @param user
     */
    @CacheInvalidate(name = "user:info:", key = "#user.id")
    public void updateUserInfo(User user) {
        userDao.updateById(user);
    }


    /**
     * 更新用户信息并同步更新缓存,数据库更新成功后，直接更新缓存值
     * <p>
     * 避免缓存穿透：不需要删除缓存再重新加载
     *
     */
    @CacheUpdate(
            name = "user:info:",
            key = "#user.id",
            value = "#user"
    )
    public void updateUser(User user) {
        userDao.updateById(user);
    }
}
