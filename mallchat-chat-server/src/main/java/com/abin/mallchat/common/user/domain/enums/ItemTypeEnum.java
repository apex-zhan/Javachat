package com.abin.mallchat.common.user.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Description: 物品枚举
 * Author: <a href="https://github.com/zongzibinbin">abin</a>
 * Date: 2023-03-19
 */
@AllArgsConstructor
@Getter
public enum ItemTypeEnum {
    MODIFY_NAME_CARD(1, "改名卡"),
    BADGE(2, "徽章"),
    ;

    private final Integer type;
    private final String desc;

    private static Map<Integer, ItemTypeEnum> cache;

    /**
     * 初始化缓存
     * 为什么要用静态代码块，因为枚举类的构造函数是私有的，不能在外部调用，所以只能在静态代码块中初始化缓存
     * 这样可以确保在类加载时就初始化缓存，避免每次调用 of 方法时都要遍历枚举值，提升性能
     */
    static {
        cache = Arrays.stream(ItemTypeEnum.values()).collect(Collectors.toMap(ItemTypeEnum::getType, Function.identity()));
    }

    /**
     * 根据类型获取物品类型枚举
     *
     * @param type
     * @return
     */
    public static ItemTypeEnum of(Integer type) {
        return cache.get(type);
    }
}
