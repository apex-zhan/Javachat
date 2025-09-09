package com.abin.mallchat.common.user.dao;

import com.abin.mallchat.common.common.domain.enums.YesOrNoEnum;
import com.abin.mallchat.common.user.domain.entity.UserBackpack;
import com.abin.mallchat.common.user.mapper.UserBackpackMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 用户背包表 服务实现类
 * </p>
 *
 * @author <a href="https://github.com/zongzibinbin">abin</a>
 * @since 2023-03-19
 */
@Service
public class UserBackpackDao extends ServiceImpl<UserBackpackMapper, UserBackpack> {
    /**
     * 获取用户背包中物品的数量
     *
     * @param uid
     * @param itemId
     * @return
     */
    public Integer getCountByValidItemId(Long uid, Long itemId) {
        return lambdaQuery().eq(UserBackpack::getUid, uid)
                .eq(UserBackpack::getItemId, itemId)
                .eq(UserBackpack::getStatus, YesOrNoEnum.NO.getStatus())
                .count();
    }

    /**
     * 获取用户背包中第一个有效的物品
     *
     * @param uid    用户ID
     * @param itemId 物品ID
     * @return 第一个有效的物品
     * sql： select * from user_backpack where uid = ? and item_id = ? and status = 0 limit 1
     */
    public UserBackpack getFirstValidItem(Long uid, Long itemId) {
        LambdaQueryWrapper<UserBackpack> wrapper = new QueryWrapper<UserBackpack>().lambda()
                .eq(UserBackpack::getUid, uid)
                .eq(UserBackpack::getItemId, itemId)
                .eq(UserBackpack::getStatus, YesOrNoEnum.NO.getStatus())
                .last("limit 1");
        return getOne(wrapper);
    }

    /**
     * 将物品设置为失效
     *
     * @param id 物品ID
     * @return 是否更新成功
     * sql: update user_backpack set status = 1 where id = ?
     */
    public boolean invalidItem(Long id) {
        UserBackpack update = new UserBackpack();
        update.setId(id);
        update.setStatus(YesOrNoEnum.YES.getStatus());
        return updateById(update);
    }

    /**
     * 根据用户ID和物品ID列表获取用户背包
     *
     * @param uid
     * @param itemIds
     * @return
     */
    public List<UserBackpack> getByItemIds(Long uid, List<Long> itemIds) {
        return lambdaQuery().eq(UserBackpack::getUid, uid)
                .in(UserBackpack::getItemId, itemIds)
                .eq(UserBackpack::getStatus, YesOrNoEnum.NO.getStatus())
                .list();
    }

    public List<UserBackpack> getByItemIds(List<Long> uids, List<Long> itemIds) {
        return lambdaQuery().in(UserBackpack::getUid, uids)
                .in(UserBackpack::getItemId, itemIds)
                .eq(UserBackpack::getStatus, YesOrNoEnum.NO.getStatus())
                .list();
    }

    /**
     * 根据幂等号查询用户背包
     *
     * @param idempotent
     * @return
     */
    public UserBackpack getByIdp(String idempotent) {
        return lambdaQuery().eq(UserBackpack::getIdempotent, idempotent).one();
    }
}
