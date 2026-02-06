package com.abin.mallchat.common.user.service.cache;

import com.abin.mallchat.common.user.dao.ItemConfigDao;
import com.abin.mallchat.common.user.domain.entity.ItemConfig;
import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.anno.Cached;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Description: 用户相关缓存
 * Author: <a href="https://github.com/zongzibinbin">abin</a>
 * Date: 2023-03-27
 */
@Component
public class ItemCache {//todo 多级缓存

    @Autowired
    private ItemConfigDao itemConfigDao;

    /**
     * 获取所有道具
     *
     * @param type
     * @return
     */
//    @Cacheable(cacheNames = "item", key = "'itemsByType:'+#type")
    @Cached(
            name = "item:byType",
            key = "'itemsByType:'+#type",
            expire = 3600,
            cacheType = CacheType.BOTH,
            localExpire =  300
    )
    public List<ItemConfig> getByType(Integer type) {
        return itemConfigDao.getByType(type);
    }

    /**
     * 根据ID获取道具
     *
     * @param itemId
     * @return
     */
    @Cacheable(cacheNames = "item", key = "'item:'+#itemId")
    public ItemConfig getById(Long itemId) {
        return itemConfigDao.getById(itemId);
    }
}
