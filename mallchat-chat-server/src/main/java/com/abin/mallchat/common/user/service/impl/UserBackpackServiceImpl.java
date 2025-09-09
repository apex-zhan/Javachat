package com.abin.mallchat.common.user.service.impl;

import com.abin.mallchat.common.MDCKey;
import com.abin.mallchat.common.common.annotation.RedissonLock;
import com.abin.mallchat.common.common.domain.enums.IdempotentEnum;
import com.abin.mallchat.common.common.domain.enums.YesOrNoEnum;
import com.abin.mallchat.common.common.event.ItemReceiveEvent;
import com.abin.mallchat.common.user.dao.UserBackpackDao;
import com.abin.mallchat.common.user.domain.entity.ItemConfig;
import com.abin.mallchat.common.user.domain.entity.User;
import com.abin.mallchat.common.user.domain.entity.UserBackpack;
import com.abin.mallchat.common.user.domain.enums.ItemTypeEnum;
import com.abin.mallchat.common.user.service.IUserBackpackService;
import com.abin.mallchat.common.user.service.cache.ItemCache;
import org.slf4j.MDC;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * <p>
 * 用户背包表 服务类
 * </p>
 *
 * @author <a href="https://github.com/zongzibinbin">abin</a>
 * @since 2023-03-19
 */
@Service
public class UserBackpackServiceImpl implements IUserBackpackService {
    @Autowired
    private UserBackpackDao userBackpackDao;
    @Autowired
    private ItemCache itemCache;
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;
    @Autowired
    @Lazy
    private UserBackpackServiceImpl userBackpackServiceImpl;

    /**
     * 用户获取一个物品
     *
     * @param uid            用户id
     * @param itemId         物品id
     * @param idempotentEnum 幂等类型
     * @param businessId     上层业务发送的唯一标识
     */
    @Override
    public void acquireItem(Long uid, Long itemId, IdempotentEnum idempotentEnum, String businessId) {
        //1. 组装幂等号
        String idempotent = getIdempotent(itemId, idempotentEnum, businessId);
        //因为是同类调用所以得使用动态代理，不然注解不生效，或者者直接注入自己
//      ((UserBackpackServiceImpl)AopContext.currentProxy()).doAcquireItem(uid, itemId, idempotent);
        userBackpackServiceImpl.doAcquireItem(uid, itemId, idempotent);
    }

    /**
     * 用户获取一个物品-加锁幂等
     *
     * @param uid
     * @param itemId
     * @param idempotent
     */
    @RedissonLock(
            key = "#idempotent",
            waitTime = 2000,
            expireTime = 20,
            scene = "acquireItem")
    private void doAcquireItem(Long uid, Long itemId, String idempotent) {
        UserBackpack userBackpack = userBackpackDao.getByIdp(idempotent);
        //1. 幂等检查
        if (Objects.nonNull(userBackpack)) {
            return;
        }
        //2. 业务检查(徽章类型唯一性检查，已经有的就不发了)
        ItemConfig itemCacheById = itemCache.getById(itemId);
        userBackpackDao.getById(itemId);
        if (ItemTypeEnum.BADGE.getType().equals(itemCacheById.getType())) {
            Integer countByValidItemId = userBackpackDao.getCountByValidItemId(uid, itemId);
            if (countByValidItemId > 0) {
                return;
            }
        }
        //3. 发物品
        UserBackpack insert = UserBackpack.builder()
                .uid(uid)
                .itemId(itemId)
                .status(YesOrNoEnum.NO.getStatus())
                .idempotent(idempotent)
                .build();
        userBackpackDao.save(userBackpack);
        //4. 用户收到物品事件
        applicationEventPublisher.publishEvent(new ItemReceiveEvent(this, insert));
        //5. 发送物品成功日志
        MDC.put(MDCKey.ITEM_ID, String.valueOf(itemId));
    }


    /**
     * 组装幂等号
     *
     * @param itemId
     * @param idempotentEnum
     * @param businessId
     * @return
     */
    private String getIdempotent(Long itemId, IdempotentEnum idempotentEnum, String businessId) {
        return String.format("%d_%d_%s", itemId, idempotentEnum.getType(), businessId);
    }
}
