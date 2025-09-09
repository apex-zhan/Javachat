package com.abin.mallchat.common.user.service.adapter;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import com.abin.mallchat.common.common.domain.enums.YesOrNoEnum;
import com.abin.mallchat.common.user.domain.entity.ItemConfig;
import com.abin.mallchat.common.user.domain.entity.User;
import com.abin.mallchat.common.user.domain.entity.UserBackpack;
import com.abin.mallchat.common.user.domain.vo.response.user.BadgeResp;
import com.abin.mallchat.common.user.domain.vo.response.user.UserInfoResp;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.bean.WxOAuth2UserInfo;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Description: 用户适配器
 * Author: <a href="https://github.com/zongzibinbin">abin</a>
 * Date: 2023-03-19
 */
@Slf4j
public class UserAdapter {

    public static User buildUser(String openId) {
        User user = new User();
        user.setOpenId(openId);
        return user;
    }

    /**
     * 构建授权用户信息。
     * @param id
     * @param userInfo
     * @return
     */
    public static User buildAuthorizeUser(Long id, WxOAuth2UserInfo userInfo) {
        User user = new User();
        user.setId(id);
        user.setAvatar(userInfo.getHeadImgUrl());
        user.setName(userInfo.getNickname());
        user.setSex(userInfo.getSex());
        if (userInfo.getNickname().length() > 6) {
            user.setName("名字过长" + RandomUtil.randomInt(100000));
        } else {
            user.setName(userInfo.getNickname());
        }
        return user;
    }

    /**
     *
     * @param userInfo
     * @param countByValidItemId
     * @return
     */
    public static UserInfoResp buildUserInfoResp(User userInfo, Integer countByValidItemId) {
        UserInfoResp userInfoResp = new UserInfoResp();
        BeanUtil.copyProperties(userInfo, userInfoResp);
        userInfoResp.setModifyNameChance(countByValidItemId);
        return userInfoResp;
    }

    /**
     * 构建用户徽章响应列表。
     * <p>
     * 将系统配置的所有徽章（ItemConfig）与用户拥有的徽章（UserBackpack）以及用户当前佩戴的徽章（User.itemId）
     * 进行整合，生成前端展示所需的徽章视图列表（BadgeResp）。
     * 排序规则：优先展示已佩戴的徽章（wearing=1），然后展示已拥有的徽章（obtain=1）。
     *
     * @param itemConfigs 系统中配置的所有徽章（物品类型为徽章）
     * @param backpacks   用户背包中拥有的徽章记录
     * @param user        用户实体（用于判断是否佩戴徽章），可能为空
     * @return 徽章响应列表
     */
    public static List<BadgeResp> buildBadgeResp(List<ItemConfig> itemConfigs, List<UserBackpack> backpacks, User user) {
        if (ObjectUtil.isNull(user)) {
            // user 可能为空，防止 user.getItemId() 引发空指针，直接返回空列表
            return Collections.emptyList();
        }
        // 将用户已拥有的徽章 itemId 收集为 Set，用于 O(1) 判断是否拥有
        Set<Long> obtainItemSet = backpacks.stream().map(UserBackpack::getItemId).collect(Collectors.toSet());
        // 遍历所有系统配置的徽章，组装前端需要的返回对象
        return itemConfigs.stream().map(a -> {
                    BadgeResp resp = new BadgeResp();
                    // 拷贝展示字段（id/img/describe）到响应对象
                    BeanUtil.copyProperties(a, resp);
                    // 是否拥有：在用户背包集合中则为 YES，否则为 NO
                    resp.setObtain(obtainItemSet.contains(a.getId()) ? YesOrNoEnum.YES.getStatus() : YesOrNoEnum.NO.getStatus());
                    // 是否佩戴：与 user.itemId 匹配则为 YES，否则为 NO；使用 ObjectUtil.equal 做空安全比较
                    resp.setWearing(ObjectUtil.equal(a.getId(), user.getItemId()) ? YesOrNoEnum.YES.getStatus() : YesOrNoEnum.NO.getStatus());
                    return resp;
                })
                // 排序：优先已佩戴（wearing=1 在前），再优先已拥有（obtain=1 在前）
                .sorted(Comparator.comparing(BadgeResp::getWearing, Comparator.reverseOrder())
                        .thenComparing(BadgeResp::getObtain, Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }
}
